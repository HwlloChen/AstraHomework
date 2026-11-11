package com.etaris.astrahomework

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Mounted Android clients hardcode this prefix; it is independent of the plugin ID. */
private const val ROUTE = "/api/plugin/astrahomework"
private val TASK_ID = Regex("[A-Za-z0-9_-]{16,128}")

/** Only encrypted remote connections (or explicit local development) may receive the bearer token. */
fun validatedBaseUrl(value: String): String {
    val raw = value.trim().trimEnd('/')
    val uri = try { URI(raw) } catch (_: Exception) { throw IllegalArgumentException("服务器地址无效") }
    val host = uri.host ?: throw IllegalArgumentException("服务器地址无效")
    val local = host.equals("localhost", true) || host == "127.0.0.1" || host == "::1" || host == "[::1]"
    require(uri.scheme.equals("https", true) || (uri.scheme.equals("http", true) && local)) {
        "服务器地址必须使用 HTTPS（本机开发可使用 HTTP localhost）"
    }
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.port <= 65535) {
        "服务器地址不能包含账号、查询参数或片段"
    }
    return raw
}

/** Coarse server-reported analysis progress; every field is optional for forward compatibility. */
data class TaskProgress(
    val stage: String? = null,
    val batch: String? = null,
    val processedPages: Int? = null,
    val totalPages: Int? = null,
) {
    /** Fraction in 0..1 when the server reported both counters, otherwise null (indeterminate). */
    val fraction: Float?
        get() {
            val total = totalPages ?: return null
            val processed = processedPages ?: return null
            if (total <= 0) return null
            return (processed.toFloat() / total).coerceIn(0f, 1f)
        }

    val label: String
        get() = when {
            processedPages != null && totalPages != null && totalPages > 0 ->
                "已分析 $processedPages / $totalPages 页"
            stage != null -> "正在${STAGE_LABELS[stage] ?: "处理"}"
            else -> "等待服务端处理"
        }

    private companion object {
        // Only the stages the server publishes; unknown values degrade to a generic label.
        val STAGE_LABELS = mapOf(
            "configuration validation" to "校验配置",
            "PDF path resolution" to "读取文件",
            "PDF extraction" to "提取 PDF 页面",
            "provider resolution" to "选择模型",
            "LLM generation" to "分析白板内容",
            "output parsing" to "整理分析结果",
        )
    }
}

data class TaskStatus(
    val status: String,
    val progress: TaskProgress,
    val homeworkPages: Int,
    val hasSummary: Boolean,
    val deliveryErrors: Int,
) {
    companion object {
        /** Parse only what the UI needs; unknown/absent fields stay null instead of failing. */
        fun from(json: JSONObject): TaskStatus {
            val progress = json.optJSONObject("progress")
            val positive = { value: Int? -> value?.takeIf { it > 0 } }
            val result = json.optJSONObject("result")
            return TaskStatus(
                status = json.optString("status"),
                progress = TaskProgress(
                    stage = progress?.optString("stage")?.takeIf(String::isNotBlank),
                    batch = progress?.optString("batch")?.takeIf(String::isNotBlank),
                    processedPages = positive(progress?.optInt("processed_pages", -1)),
                    totalPages = positive(progress?.optInt("total_pages", -1)),
                ),
                homeworkPages = result?.optJSONArray("homework_pages")?.length() ?: 0,
                hasSummary = result?.optString("summary").orEmpty().isNotBlank(),
                deliveryErrors = json.optJSONArray("delivery_errors")?.length() ?: 0,
            )
        }
    }
}

class HomeworkRepository(private val context: Context) {
    suspend fun scan(folder: String): List<HomeworkFile> = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folder))
        require(tree?.canRead() == true) { "无法读取作业文件夹，请重新选择" }
        tree.listFiles().asSequence()
            .onEach { coroutineContext.ensureActive() }
            .filter { it.isFile && it.name?.endsWith(".pdf", ignoreCase = true) == true }
            .map { HomeworkFile(it.name ?: "作业.pdf", it.uri, it.lastModified(), it.length(), false) }
            .sortedByDescending(HomeworkFile::modified)
            .toList()
    }

    suspend fun health(url: String, token: String): String {
        val response = request(url, token)
        require(response.optString("status") == "ok") { "服务器健康检查未通过" }
        val className = (response.opt("class_name") as? String)?.trim().orEmpty()
        require(className.isNotEmpty()) { "服务器未返回有效班级名称，请检查令牌和服务端配置" }
        return className
    }

    suspend fun upload(url: String, token: String, file: HomeworkFile): String {
        val id = request(url, token, file).optString("id")
        require(TASK_ID.matches(id)) { "服务器未返回有效任务 ID；请在服务端确认上传结果，勿重复上传" }
        return id
    }

    suspend fun task(url: String, token: String, id: String): TaskStatus {
        require(TASK_ID.matches(id)) { "任务 ID 无效" }
        return TaskStatus.from(request(url, token, taskId = id))
    }

    private suspend fun request(
        baseUrl: String,
        token: String,
        file: HomeworkFile? = null,
        taskId: String? = null,
    ): JSONObject = suspendCancellableCoroutine { continuation ->
        val activeConnection = AtomicReference<HttpURLConnection?>()
        val future = networkExecutor.submit {
            if (!continuation.isActive) return@submit
            try {
                val result = blockingRequest(baseUrl, token, file, taskId) { connection ->
                    activeConnection.set(connection)
                    if (!continuation.isActive) {
                        connection.disconnect()
                        throw CancellationException()
                    }
                }
                continuation.resumeWith(Result.success(result))
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            } finally {
                activeConnection.set(null)
            }
        }
        continuation.invokeOnCancellation {
            // Interrupt file copying and close the socket rather than merely ignoring its result.
            future.cancel(true)
            activeConnection.getAndSet(null)?.disconnect()
        }
    }

    private fun blockingRequest(
        baseUrl: String,
        token: String,
        file: HomeworkFile?,
        taskId: String?,
        register: (HttpURLConnection) -> Unit,
    ): JSONObject {
        require(token.isNotBlank()) { "请填写访问令牌" }
        val path = when {
            taskId != null -> "/tasks/$taskId"
            file != null -> "/upload"
            else -> "/health"
        }
        val connection = URI(validatedBaseUrl(baseUrl) + ROUTE + path).toURL()
            .openConnection() as HttpURLConnection
        try {
            register(connection)
            // Do not forward Authorization to an unexpected redirect (including HTTP downgrades).
            connection.instanceFollowRedirects = false
            connection.requestMethod = if (file == null) "GET" else "POST"
            connection.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            connection.connectTimeout = 15_000
            // Uploads may include PDF extraction plus validation on the server.
            connection.readTimeout = 135_000
            if (file != null) writePdf(connection, file)
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(code != 413) {
                "上传被拒绝（413）：可能是 Nginx 等反向代理的请求大小限制，也可能是插件的 30 MiB PDF 限制；仅凭此响应无法确定来源。请缩小 PDF 或请管理员检查代理与插件限制。"
            }
            // Never display server bodies: they can reflect credentials or other sensitive input.
            require(code in 200..299) {
                "服务端错误 $code" + if (code == 401 || code == 403) "：请检查访问令牌及班级授权" else "：请检查服务端或代理配置"
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Stream the PDF with chunked encoding so large files are never buffered in memory. */
    private fun writePdf(connection: HttpURLConnection, file: HomeworkFile) {
        val boundary = "Homework${UUID.randomUUID().toString().replace("-", "")}"
        connection.doOutput = true
        connection.setChunkedStreamingMode(DEFAULT_BUFFER_SIZE)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.outputStream.use { output ->
            // Quotes and newlines would break the multipart header, so neutralize them.
            val safeName = file.name.replace(Regex("[\\r\\n\"]"), "_")
            output.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; " +
                "filename=\"$safeName\"\r\nContent-Type: application/pdf\r\n\r\n").toByteArray())
            val input = requireNotNull(context.contentResolver.openInputStream(file.uri)) { "无法打开 PDF 文件" }
            input.use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw CancellationException()
                    val count = stream.read(buffer)
                    if (count == -1) break
                    output.write(buffer, 0, count)
                }
            }
            output.write(("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"modified\"\r\n" +
                "Content-Type: text/plain\r\n\r\n${file.modified}\r\n--$boundary--\r\n").toByteArray())
        }
    }

    private companion object {
        // One shared bounded pool; no executor is retained per Activity or ViewModel.
        val networkExecutor = Executors.newFixedThreadPool(2) { work ->
            Thread(work, "AstraHomework-network").apply { isDaemon = true }
        }
    }
}
