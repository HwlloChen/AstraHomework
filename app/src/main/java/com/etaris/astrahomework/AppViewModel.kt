package com.etaris.astrahomework

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

// Long PDFs need many batches; polling stays bounded and always resumable by the user.
private const val POLL_ATTEMPTS = 120
private val POLL_INTERVAL = 3.seconds

private data class ConnectionIdentity(val url: String, val token: String) {
    override fun toString() = "ConnectionIdentity(redacted)"
}

data class PendingTask(
    val id: String,
    val fileUri: String,
    val baseUrl: String,
    val token: String,
    val progress: TaskProgress = TaskProgress(),
) {
    override fun toString() = "PendingTask(id=$id, credentials=redacted)"
}

data class UiState(
    val settings: Settings = Settings(),
    val draft: Settings? = null,
    val settingsLoaded: Boolean = false,
    val files: List<HomeworkFile> = emptyList(),
    val busyFile: String? = null,
    val pendingTask: PendingTask? = null,
    val message: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val settingsOpen: Boolean = false,
    val aboutOpen: Boolean = false,
    val className: String? = null,
    val draftClassName: String? = null,
    val verifyingClass: Boolean = false,
    val update: AboutUpdateState = AboutUpdateState(),
) {
    val configured get() = settings.folder.isNotBlank() && settings.baseUrl.isNotBlank() && settings.token.isNotBlank()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val storage = HomeworkStorage(app)
    private val repository = HomeworkRepository(app)
    private val updateChecker = UpdateChecker(BuildConfig.GITHUB_REPO, BuildConfig.VERSION_NAME)
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var updateJob: Job? = null
    private var scanJob: Job? = null
    private var scanGeneration = 0L
    private var requestJob: Job? = null
    private var requestGeneration = 0L
    private val historyMutex = Mutex()
    private var committedIdentity: ConnectionIdentity? = null
    private var committedJob: Job? = null
    private var committedGeneration = 0L
    private var authJob: Job? = null
    private var authGeneration = 0L

    private fun identity(settings: Settings) = ConnectionIdentity(
        settings.baseUrl.trim().trimEnd('/'), settings.token.trim(),
    )

    init {
        viewModelScope.launch {
            storage.settings.collectLatest { settings ->
                val folderChanged = settings.folder != _state.value.settings.folder
                _state.update { it.copy(settings = settings, draft = it.draft ?: settings, settingsLoaded = true) }
                val connection = identity(settings)
                if (connection != committedIdentity) {
                    committedIdentity = connection
                    verifyCommitted(settings, connection)
                }
                if (folderChanged) {
                    scanJob?.cancel()
                    ++scanGeneration
                    _state.update { it.copy(files = emptyList(), loading = false) }
                    if (settings.folder.isNotBlank()) scan()
                }
            }
        }
        viewModelScope.launch {
            state.map { it.files.map { file -> Triple(file.uri, file.firstDiscoveredAt, file.isUploaded) } }
                .distinctUntilChanged().collectLatest {
                    // A scan/history change immediately reschedules the nearest expiry.
                    while (true) {
                        refreshHighlights()
                        val now = System.currentTimeMillis()
                        val nextExpiry = _state.value.files.filter { it.isNew }.mapNotNull { it.firstDiscoveredAt }
                            .map { (NEW_FILE_WINDOW_MILLIS - (now - it).coerceAtLeast(0L)).coerceAtLeast(1L) }.minOrNull()
                        // Clock changes are also observed within a minute; no provider/network work.
                        delay((nextExpiry ?: 60_000L).coerceAtMost(60_000L).milliseconds)
                    }
                }
        }
    }

    private fun refreshHighlights() {
        val now = System.currentTimeMillis()
        _state.update { state -> state.copy(files = state.files.map {
            it.copy(isNew = isNewFile(it.firstDiscoveredAt, it.isUploaded, now))
        }) }
    }

    fun onResume() {
        refreshHighlights()
        if (_state.value.settingsLoaded) scan()
    }

    private fun verifyCommitted(settings: Settings, connection: ConnectionIdentity) {
        committedJob?.cancel()
        val generation = ++committedGeneration
        val draftGeneration = authGeneration
        _state.update { it.copy(className = null, verifyingClass = settings.token.isNotBlank()) }
        if (settings.token.isBlank()) return
        committedJob = viewModelScope.launch {
            try {
                val name = repository.health(settings.baseUrl, settings.token)
                if (generation == committedGeneration && connection == identity(_state.value.settings)) {
                    _state.update { it.copy(className = name, draftClassName =
                        if (draftGeneration == authGeneration && connection == identity(it.draft ?: it.settings)) name else it.draftClassName) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == committedGeneration) _state.update {
                    it.copy(className = null, message = "已保存连接验证失败，请在设置中检查地址与令牌")
                }
            } finally {
                if (generation == committedGeneration) _state.update { it.copy(verifyingClass = false) }
            }
        }
    }

    fun editUrl(value: String) = editDraft { it.copy(baseUrl = value) }
    fun editToken(value: String) = editDraft { it.copy(token = value) }
    private fun editDraft(edit: (Settings) -> Settings) {
        if (_state.value.saving) return
        val before = _state.value.draft ?: _state.value.settings
        val after = edit(before)
        if (after.baseUrl != before.baseUrl || after.token != before.token) {
            ++authGeneration
            authJob?.cancel()
            _state.update { it.copy(draft = after, draftClassName = null,
                busyFile = if (it.busyFile == "health") null else it.busyFile, message = null) }
        } else _state.update { it.copy(draft = after) }
    }

    fun saveSettings(settings: Settings) {
        if (_state.value.saving || (_state.value.busyFile != null && _state.value.busyFile != "health")) return
        val normalized = try {
            require(settings.folder.isNotBlank()) { "请选择作业文件夹" }
            require(settings.token.isNotBlank()) { "请填写访问令牌" }
            settings.copy(baseUrl = validatedBaseUrl(settings.baseUrl), token = settings.token.trim())
        } catch (error: IllegalArgumentException) {
            _state.update { it.copy(message = error.message) }
            return
        }
        authJob?.cancel()
        val generation = ++authGeneration
        _state.update { it.copy(saving = true, busyFile = null, message = null, draftClassName = null) }
        authJob = viewModelScope.launch {
            try {
                val name = repository.health(normalized.baseUrl, normalized.token)
                if (generation != authGeneration) return@launch
                // Once validation succeeds, finish persistence and UI commit together.
                withContext(NonCancellable) {
                    storage.save(normalized)
                    committedJob?.cancel()
                    ++committedGeneration
                    committedIdentity = identity(normalized)
                    _state.update { it.copy(settings = normalized, draft = normalized, className = name,
                        files = if (it.settings.folder != normalized.folder) emptyList() else it.files,
                        draftClassName = name, verifyingClass = false, settingsOpen = false, message = "设置已验证并保存") }
                    scan()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == authGeneration) _state.update { it.copy(message = "验证或保存失败，请检查服务器地址、令牌及班级配置；原设置未被替换") }
            } finally {
                if (generation == authGeneration) _state.update { it.copy(saving = false) }
            }
        }
    }

    fun setFolder(uri: Uri) {
        if (_state.value.saving) return
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            // Only edit the draft. Selecting a folder must not save credentials or leave settings.
            editDraft { it.copy(folder = uri.toString()) }
        } catch (_: SecurityException) {
            _state.update { it.copy(message = "无法保存文件夹访问权限，请重试") }
        }
    }

    fun openSettings() = _state.update { it.copy(settingsOpen = true, aboutOpen = false) }
    fun closeSettings() {
        if (_state.value.configured && !_state.value.saving) {
            _state.update { it.copy(settingsOpen = false) }
        }
    }

    fun openAbout() {
        _state.update { it.copy(aboutOpen = true, settingsOpen = false) }
        // Opening the page is the only trigger; no polling or background check runs.
        if (_state.value.update.checkedVersion == null) checkUpdate()
    }

    fun closeAbout() {
        updateJob?.cancel()
        _state.update { it.copy(aboutOpen = false, update = it.update.copy(checking = false)) }
    }

    /** Toggles whether CI / pre-release builds are included in update checks. */
    fun togglePrerelease(enabled: Boolean) {
        // Update the in-memory settings first; the settings flow echoes the same
        // values, so a later connection save can never revert the toggle.
        _state.update {
            it.copy(
                settings = it.settings.copy(checkPrerelease = enabled),
                draft = it.draft?.copy(checkPrerelease = enabled),
                // Invalidate the last check so reopening About re-queries.
                update = AboutUpdateState(),
            )
        }
        viewModelScope.launch {
            storage.saveUpdatePreferences(enabled, _state.value.settings.useProxy)
        }
    }

    /** Toggles whether update checks are routed through gh-proxy.com. */
    fun toggleProxy(enabled: Boolean) {
        _state.update {
            it.copy(
                settings = it.settings.copy(useProxy = enabled),
                draft = it.draft?.copy(useProxy = enabled),
                update = AboutUpdateState(),
            )
        }
        viewModelScope.launch {
            storage.saveUpdatePreferences(_state.value.settings.checkPrerelease, enabled)
        }
    }

    /** Asks GitHub for the latest release; failures are shown inline, never thrown. */
    fun checkUpdate() {
        if (_state.value.update.checking) return
        updateJob?.cancel()
        val settings = _state.value.settings
        _state.update { it.copy(update = it.update.copy(checking = true, error = null)) }
        updateJob = viewModelScope.launch {
            val result = try {
                if (settings.checkPrerelease) {
                    updateChecker.prerelease(useProxy = settings.useProxy)
                } else {
                    updateChecker.stable(useProxy = settings.useProxy)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                UpdateCheckResult.Unreachable
            }
            _state.update {
                it.copy(
                    update = AboutUpdateState(
                        checking = false,
                        release = (result as? UpdateCheckResult.NewRelease)?.info,
                        // Only a failed request is an error; "no release yet" means up to date.
                        error = if (result is UpdateCheckResult.Unreachable)
                            "暂时无法获取更新信息，请检查网络后重试" else null,
                        checkedVersion = BuildConfig.VERSION_NAME,
                    ),
                )
            }
        }
    }

    fun scan() {
        val folder = _state.value.settings.folder.takeIf(String::isNotBlank) ?: return
        scanJob?.cancel()
        val generation = ++scanGeneration
        _state.update { it.copy(loading = true) }
        scanJob = viewModelScope.launch {
            try {
                val files = repository.scan(folder)
                // Serialize history read/publication with upload marking; no stale scan can undo it.
                historyMutex.withLock {
                    if (generation == scanGeneration && folder == _state.value.settings.folder) {
                        val now = System.currentTimeMillis()
                        storage.recordDiscovery(files.map { it.uri.toString() }, now)
                        val history = storage.history()
                        if (generation == scanGeneration && folder == _state.value.settings.folder) {
                            _state.update { it.copy(files = files.map { file ->
                                val uploaded = file.uri.toString() in history.seenUris
                                val discovered = history.firstDiscovery[file.uri.toString()]
                                file.copy(isUploaded = uploaded, firstDiscoveredAt = discovered,
                                    isNew = isNewFile(discovered, uploaded, System.currentTimeMillis()))
                            }) }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == scanGeneration) _state.update { it.copy(message = error.message ?: "扫描失败") }
            } finally {
                if (generation == scanGeneration) _state.update { it.copy(loading = false) }
            }
        }
    }

    fun health(url: String, token: String) {
        if (_state.value.saving || (_state.value.busyFile != null && _state.value.busyFile != "health")) return
        val connection = identity(Settings(baseUrl = url, token = token))
        if (connection != identity(_state.value.draft ?: _state.value.settings)) return
        authJob?.cancel()
        if (connection == identity(_state.value.settings)) {
            committedJob?.cancel()
            ++committedGeneration
            _state.update { it.copy(className = null, verifyingClass = false) }
        }
        val generation = ++authGeneration
        _state.update { it.copy(busyFile = "health", draftClassName = null, message = null) }
        authJob = viewModelScope.launch {
            try {
                val name = repository.health(url, token)
                if (generation == authGeneration && connection == identity(_state.value.draft ?: _state.value.settings)) {
                    // A draft check can never relabel a different committed connection.
                    if (connection == identity(_state.value.settings)) {
                        committedJob?.cancel()
                        ++committedGeneration
                        _state.update { it.copy(className = name, verifyingClass = false) }
                    }
                    _state.update { it.copy(draftClassName = name, message = "连接成功，班级已验证") }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == authGeneration) {
                    if (connection == identity(_state.value.settings)) {
                        committedJob?.cancel()
                        ++committedGeneration
                        _state.update { it.copy(className = null, verifyingClass = false) }
                    }
                    _state.update { it.copy(message = "连接验证失败，请检查地址、令牌和班级配置") }
                }
            } finally {
                if (generation == authGeneration) _state.update { it.copy(busyFile = null) }
            }
        }
    }
    fun upload(file: HomeworkFile) {
        // There is already an accepted upload: only poll it, never send that PDF again to resume.
        if (_state.value.pendingTask != null) {
            resumeTask()
            return
        }
        val settings = _state.value.settings
        request(settings.baseUrl, settings.token, file)
    }

    fun cancelRequest() {
        if (_state.value.busyFile == null) return
        if (_state.value.busyFile == "health") {
            ++authGeneration
            authJob?.cancel()
            _state.update { it.copy(busyFile = null, draftClassName = null, message = "已取消连接验证") }
            return
        }
        ++requestGeneration
        requestJob?.cancel()
        requestJob = null
        _state.update {
            it.copy(busyFile = null, message = if (it.pendingTask != null)
                "已停止查看进度；服务端任务仍在运行，可继续查看" else
                "已取消本地请求；若上传已到达服务端，任务可能仍在运行，请勿立即重复上传")
        }
    }

    fun resumeTask() {
        val task = _state.value.pendingTask ?: return
        startRequest(task.fileUri) { poll(task) }
    }

    private fun request(url: String, token: String, file: HomeworkFile) {
        startRequest(file.uri.toString()) {
            val id = repository.upload(url, token, file)
            val task = PendingTask(id, file.uri.toString(), url, token.trim())
            // Preserve accepted-upload history even if the user stops polling immediately.
            withContext(NonCancellable) {
                _state.update { it.copy(pendingTask = task, message = "已上传，正在分析") }
                historyMutex.withLock {
                    storage.markSeen(file.uri)
                    _state.update { it.copy(files = it.files.map { entry ->
                        if (entry.uri == file.uri) entry.copy(isNew = false, isUploaded = true) else entry
                    }) }
                }
            }
            poll(task)
        }
    }

    private fun startRequest(busy: String, work: suspend () -> Unit) {
        if (_state.value.busyFile != null || _state.value.saving) return
        val generation = ++requestGeneration
        // Reserve synchronously, before launch, so rapid taps cannot upload twice.
        _state.update { it.copy(busyFile = busy, message = null) }
        requestJob = viewModelScope.launch {
            try {
                work()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == requestGeneration) _state.update { current ->
                    var message = error.message ?: "请求失败"
                    listOf(current.settings.token, current.draft?.token, current.pendingTask?.token)
                        .filterNotNull().map(String::trim).filter(String::isNotEmpty)
                        .forEach { message = message.replace(it, "[令牌已隐藏]") }
                    current.copy(message = message)
                }
            } finally {
                if (generation == requestGeneration) _state.update { it.copy(busyFile = null) }
            }
        }
    }

    /**
     * Poll until completion, failure or the local attempt budget is exhausted.
     *
     * Each response publishes the server-reported progress so multi-batch PDFs show
     * real page counts instead of an indeterminate spinner; stopping locally never
     * cancels server-side work and never re-sends the PDF.
     */
    private suspend fun poll(task: PendingTask) {
        repeat(POLL_ATTEMPTS) {
            delay(POLL_INTERVAL)
            val status = repository.task(task.baseUrl, task.token, task.id)
            when (status.status) {
                "completed" -> {
                    val details = buildString {
                        append("处理完成 · 作业 ").append(status.homeworkPages).append(" 页")
                        if (status.hasSummary) append(" · 总结已生成")
                        if (status.deliveryErrors > 0) {
                            append(" · ").append(status.deliveryErrors).append(" 个群推送失败")
                        }
                    }
                    _state.update { it.copy(pendingTask = null, message = details) }
                    return
                }
                "failed" -> {
                    _state.update { it.copy(pendingTask = null) }
                    error("服务端分析失败，请联系管理员检查任务日志")
                }
            }
            _state.update { state ->
                val current = state.pendingTask ?: return@update state
                if (current.id != task.id) state else state.copy(
                    pendingTask = current.copy(progress = status.progress),
                    message = status.progress.label,
                )
            }
        }
        _state.update { it.copy(message = "分析仍在进行 · 点击继续查看进度（不会再次上传 PDF）") }
    }
}
