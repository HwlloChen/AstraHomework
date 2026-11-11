package com.etaris.astrahomework

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etaris.astrahomework.ui.theme.AstraHomeworkTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeworkApp(viewModel: AppViewModel, chooseFolder: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Let the IME consume Back first. Back from optional settings returns home, retaining the draft.
    BackHandler(enabled = state.aboutOpen) { viewModel.closeAbout() }
    BackHandler(enabled = state.settingsOpen && state.configured && !state.saving) {
        viewModel.closeSettings()
    }
    BackHandler(enabled = state.saving) { /* Do not leave during an atomic settings save. */ }
    HomeworkContent(
        state = state,
        chooseFolder = chooseFolder,
        save = viewModel::saveSettings,
        closeSettings = viewModel::closeSettings,
        openSettings = viewModel::openSettings,
        openAbout = viewModel::openAbout,
        closeAbout = viewModel::closeAbout,
        checkUpdate = viewModel::checkUpdate,
        openLink = { url ->
            // Only HTTPS leaves the app; the About page never builds other schemes.
            if (url.startsWith("https://")) runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        },
        editUrl = viewModel::editUrl,
        editToken = viewModel::editToken,
        health = viewModel::health,
        refresh = viewModel::scan,
        upload = viewModel::upload,
        cancel = viewModel::cancelRequest,
        resume = viewModel::resumeTask,
    )
}

/** Pure UI shared by the runtime and previews; no ViewModel, picker or network in previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeworkContent(
    state: UiState,
    chooseFolder: () -> Unit = {},
    save: (Settings) -> Unit = {},
    closeSettings: () -> Unit = {},
    openSettings: () -> Unit = {},
    openAbout: () -> Unit = {},
    closeAbout: () -> Unit = {},
    checkUpdate: () -> Unit = {},
    openLink: (String) -> Unit = {},
    editUrl: (String) -> Unit = {},
    editToken: (String) -> Unit = {},
    health: (String, String) -> Unit = { _, _ -> },
    refresh: () -> Unit = {},
    upload: (HomeworkFile) -> Unit = {},
    cancel: () -> Unit = {},
    resume: () -> Unit = {},
) {
    val showingSettings = state.settingsOpen || !state.configured
    val focus = LocalFocusManager.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 840.dp
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                !state.settingsLoaded -> "AstraHomework"
                                state.aboutOpen -> "关于"
                                showingSettings -> "连接设置"
                                else -> "AstraHomework"
                            },
                        )
                    },
                    navigationIcon = {
                        if (state.settingsLoaded && (state.aboutOpen || (showingSettings && state.configured))) {
                            IconButton(
                                onClick = {
                                    focus.clearFocus()
                                    if (state.aboutOpen) closeAbout() else closeSettings()
                                },
                                enabled = !state.saving,
                            ) { AppIcon(R.drawable.ic_back, "返回作业首页", mirrorInRtl = true) }
                        }
                    },
                    actions = {
                        if (!showingSettings && !state.aboutOpen && state.settingsLoaded) {
                            IconButton(onClick = refresh, enabled = !state.loading) {
                                AppIcon(R.drawable.ic_refresh, "刷新 PDF 文件列表")
                            }
                        }
                        if (!useRail && state.settingsLoaded) IconButton(
                            onClick = {
                                focus.clearFocus()
                                if (state.aboutOpen) closeAbout() else openAbout()
                            },
                        ) { AppIcon(R.drawable.ic_info, "关于与更新") }
                    },
                )
            },
        ) { padding ->
            if (!state.settingsLoaded) {
                Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "正在读取设置" })
                    Text("正在准备工作台…", Modifier.padding(top = 16.dp))
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    if (useRail) NavigationRail(
                        modifier = Modifier.fillMaxHeight().padding(padding).consumeWindowInsets(padding),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Spacer(Modifier.height(24.dp))
                        NavigationRailItem(
                            selected = !showingSettings && !state.aboutOpen,
                            onClick = { focus.clearFocus(); closeAbout(); closeSettings() },
                            enabled = state.configured && !state.saving,
                            icon = { AppIcon(R.drawable.ic_home, null) },
                            label = { Text("作业") },
                        )
                        NavigationRailItem(
                            selected = showingSettings && !state.aboutOpen,
                            onClick = openSettings,
                            enabled = !state.saving,
                            icon = { AppIcon(R.drawable.ic_settings, null) },
                            label = { Text("设置") },
                        )
                        NavigationRailItem(
                            selected = state.aboutOpen,
                            onClick = { focus.clearFocus(); openAbout() },
                            icon = { AppIcon(R.drawable.ic_info, null) },
                            label = { Text("关于") },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        when {
                            state.aboutOpen -> AboutScreen(state.update, padding, checkUpdate, openLink)
                            showingSettings -> SettingsScreen(
                                state, padding, chooseFolder, save, editUrl, editToken, health, cancel,
                            )
                            else -> HomeScreen(state, padding, refresh, openSettings, upload, cancel, resume)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: UiState,
    insets: PaddingValues,
    refresh: () -> Unit,
    settings: () -> Unit,
    upload: (HomeworkFile) -> Unit,
    cancel: () -> Unit,
    resume: () -> Unit,
) {
    val direction = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(300.dp),
        modifier = Modifier.widthIn(max = 1200.dp).fillMaxSize().consumeWindowInsets(insets),
        contentPadding = PaddingValues(
            start = insets.calculateStartPadding(direction) + 20.dp,
            end = insets.calculateEndPadding(direction) + 20.dp,
            top = insets.calculateTopPadding() + 20.dp,
            bottom = insets.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "overview", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val newFiles = state.files.filter(HomeworkFile::isNew)
                val activeFile = state.files.firstOrNull { it.uri.toString() == state.busyFile }
                val (statusTitle, statusDetail) = when {
                    state.pendingTask != null -> {
                        val taskFile = state.files.firstOrNull { it.uri.toString() == state.pendingTask.fileUri }
                        val progress = state.pendingTask.progress
                        if (state.busyFile != null) {
                            "正在查看处理进度" to
                                "${taskFile?.name ?: "PDF 已上传"} · ${progress.label}"
                        } else {
                            "还有一个任务待确认" to "PDF 已提交到服务端。点击下方「继续查看」查询结果，无需重复上传。"
                        }
                    }
                    state.busyFile == "health" -> "正在检查服务连接" to "正在验证服务器地址与访问令牌，请稍候。"
                    state.busyFile != null -> "正在上传作业文件" to "${activeFile?.name ?: "PDF 文件"} · 上传完成后将查询 AI 分析与推送结果。"
                    state.loading -> "正在检查作业文件夹" to "正在读取 PDF 列表…"
                    newFiles.isNotEmpty() -> "发现 ${newFiles.size} 个新文件" to
                        "最近发现：${newFiles.first().name}"
                    state.files.isEmpty() -> "暂无 PDF 文件" to "将白板作业 PDF 放入所选文件夹，然后点击刷新检查。"
                    state.files.all(HomeworkFile::isUploaded) -> "当前文件均已上传" to "共 ${state.files.size} 个 PDF 文件。"
                    else -> "暂无新文件" to "共 ${state.files.size} 个 PDF 文件。"
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                    Column(
                        Modifier.fillMaxWidth().animateContentSize(animationSpec = spring()).padding(24.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(statusTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                        Text(statusDetail, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(state.files.size.toString(), "PDF 文件", R.drawable.ic_pdf, Modifier.weight(1f))
                    SummaryCard(state.files.count(HomeworkFile::isNew).toString(), "新文件", R.drawable.ic_folder, Modifier.weight(1f))
                }
                RequestStatus(state, cancel, resume)
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = "正在扫描作业文件夹" })
                    Text("正在扫描 PDF 文件…", style = MaterialTheme.typography.bodyMedium)
                }
                Text("最近的文件", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            }
        }
        if (state.files.isEmpty() && !state.loading) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            Card {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(R.drawable.ic_folder, null)
                    Text("这里还没有 PDF", style = MaterialTheme.typography.titleLarge)
                    Text("将作业 PDF 放入所选文件夹，然后刷新。也可以在设置中更换文件夹。")
                    OutlinedButton(onClick = refresh) { AppIcon(R.drawable.ic_refresh, null); Spacer(Modifier.width(8.dp)); Text("刷新文件") }
                    TextButton(onClick = settings) { Text("更换文件夹") }
                }
            }
        }
        items(state.files, key = { it.uri.toString() }) { file ->
            HomeworkCard(file, state.busyFile == file.uri.toString(), state.busyFile == null && state.pendingTask == null) { upload(file) }
        }
    }
}

@Composable
private fun HomeworkCard(file: HomeworkFile, busy: Boolean, canUpload: Boolean, upload: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (file.isNew) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (file.isNew) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(R.drawable.ic_pdf, null)
                Text(when { file.isUploaded -> "已上传"; file.isNew -> "新文件"; else -> "旧文件" }, style = MaterialTheme.typography.labelLarge)
            }
            Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                "最后修改：${if (file.modified > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.modified)) else "未知"} · ${file.size / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = upload,
                enabled = canUpload,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "上传并处理 ${file.name}" },
            ) {
                AppIcon(R.drawable.ic_upload, null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "处理中…" else "上传并处理")
            }
        }
    }
}

@Composable
private fun SummaryCard(value: String, label: String, @DrawableRes icon: Int, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppIcon(icon, null)
            Text(value, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RequestStatus(state: UiState, cancel: () -> Unit, resume: (() -> Unit)? = null) {
    if (state.message != null || state.busyFile != null || state.pendingTask != null) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.fillMaxWidth().animateContentSize(animationSpec = spring()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    state.message ?: if (state.busyFile == "health") "正在测试连接…" else "正在上传或查看任务进度…",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.pendingTask?.let { Text("服务端任务：${it.id}", style = MaterialTheme.typography.bodySmall) }
                if (state.busyFile != null) {
                    val fraction = state.pendingTask?.progress?.fraction
                    if (fraction == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = state.pendingTask?.progress?.label.orEmpty()
                            },
                        )
                    }
                    TextButton(onClick = cancel) { Text(if (state.pendingTask != null) "停止查看进度" else "取消请求") }
                } else if (state.pendingTask != null && resume != null) {
                    FilledTonalButton(onClick = resume) { AppIcon(R.drawable.ic_refresh, null); Spacer(Modifier.width(8.dp)); Text("继续查看进度") }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    insets: PaddingValues,
    chooseFolder: () -> Unit,
    save: (Settings) -> Unit,
    editUrl: (String) -> Unit,
    editToken: (String) -> Unit,
    health: (String, String) -> Unit,
    cancel: () -> Unit,
) {
    val draft = state.draft ?: state.settings
    val focus = LocalFocusManager.current
    val direction = LocalLayoutDirection.current
    LazyColumn(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().consumeWindowInsets(insets),
        contentPadding = PaddingValues(
            start = insets.calculateStartPadding(direction) + 24.dp,
            end = insets.calculateEndPadding(direction) + 24.dp,
            top = insets.calculateTopPadding() + 24.dp,
            bottom = insets.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("连接你的工作台", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("先选择 PDF 文件夹，再填写 AstrBot 服务地址与访问令牌。令牌决定班级，保存前会验证连接；验证通过后才生效。", Modifier.padding(top = 8.dp))
            Text("当前班级（已保存连接）：${state.className ?: if (state.verifyingClass) "正在验证…" else "未验证"}", Modifier.padding(top = 12.dp))
            Text("草稿班级：${state.draftClassName ?: "未验证，请测试连接或保存"}", Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("作业文件夹", style = MaterialTheme.typography.titleMedium)
                    Text(if (draft.folder.isBlank()) "尚未选择" else "已获得文件夹读取权限", style = MaterialTheme.typography.bodyMedium)
                    if (draft.folder.isNotBlank()) Text(Uri.decode(draft.folder.substringAfterLast('/')), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { focus.clearFocus(); chooseFolder() }, enabled = !state.saving) {
                        AppIcon(R.drawable.ic_folder, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (draft.folder.isBlank()) "选择作业文件夹" else "更换文件夹")
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = draft.baseUrl, onValueChange = editUrl, modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving, label = { Text("服务器地址") }, singleLine = true,
                supportingText = { Text("使用 HTTPS；仅本机开发允许 HTTP localhost。") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            )
        }
        item {
            OutlinedTextField(
                value = draft.token, onValueChange = editToken, modifier = Modifier.fillMaxWidth(),
                enabled = !state.saving, label = { Text("访问令牌") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            )
        }
        item { RequestStatus(state, cancel) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { focus.clearFocus(); health(draft.baseUrl, draft.token) },
                    enabled = !state.saving && state.busyFile == null && draft.baseUrl.isNotBlank() && draft.token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { AppIcon(R.drawable.ic_check, null); Spacer(Modifier.width(8.dp)); Text("测试连接") }
                Button(
                    onClick = { focus.clearFocus(); save(draft) },
                    enabled = !state.saving && state.busyFile == null && draft.folder.isNotBlank() && draft.baseUrl.isNotBlank() && draft.token.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.saving) "正在保存…" else "保存并进入首页") }
            }
        }
    }
}

@Composable
private fun AppIcon(@DrawableRes resource: Int, description: String?, mirrorInRtl: Boolean = false) {
    val mirror = mirrorInRtl && LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(painterResource(resource), contentDescription = description, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = if (mirror) -1f else 1f })
}

private val previewSettings = Settings("content://preview/tree/Homework", "https://homework.example.com", "preview-token")
private val previewState = UiState(
    settings = previewSettings, settingsLoaded = true,
    className = "高一（3）班", draftClassName = "高一（3）班",
    files = listOf(
        HomeworkFile("数学 · 第一单元练习.pdf", Uri.parse("content://preview/1"), 1_735_689_600_000, 248_000, true),
        HomeworkFile("英语阅读与写作.pdf", Uri.parse("content://preview/2"), 1_735_603_200_000, 1_024_000, false, isUploaded = true),
        HomeworkFile("物理课后练习.pdf", Uri.parse("content://preview/3"), 0, 516_000, false),
    ),
)

@Preview(name = "Home · Phone", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun HomePhonePreview() { AstraHomeworkTheme(dynamicColor = false) { HomeworkContent(previewState) } }

@Preview(name = "Home · Tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun HomeTabletPreview() { AstraHomeworkTheme(dynamicColor = false) { HomeworkContent(previewState) } }

@Preview(name = "Settings · Phone", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun SettingsPhonePreview() { AstraHomeworkTheme(dynamicColor = false) { HomeworkContent(previewState.copy(settingsOpen = true, draft = previewSettings)) } }

@Preview(name = "Settings · Tablet · Dark", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun SettingsTabletPreview() { AstraHomeworkTheme(darkTheme = true, dynamicColor = false) { HomeworkContent(previewState.copy(settingsOpen = true, draft = previewSettings)) } }

private val previewUpdate = AboutUpdateState(
    release = ReleaseInfo(
        version = "v1.3",
        pageUrl = "https://github.com/HwlloChen/AstraHomework/releases/tag/v1.3",
        apkUrl = "https://github.com/HwlloChen/AstraHomework/releases/download/v1.3/AstraHomework-1.3.apk",
        notes = "- 新增关于页面与更新检查\n- 修复无作业白板的总结失败\n- 长 PDF 分析更快",
    ),
    checkedVersion = "1.2",
)

@Preview(name = "About · Phone", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
fun AboutPhonePreview() { AstraHomeworkTheme(dynamicColor = false) { HomeworkContent(previewState.copy(aboutOpen = true, update = previewUpdate)) } }

@Preview(name = "About · Tablet · Dark", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun AboutTabletPreview() { AstraHomeworkTheme(darkTheme = true, dynamicColor = false) { HomeworkContent(previewState.copy(aboutOpen = true, update = previewUpdate)) } }
