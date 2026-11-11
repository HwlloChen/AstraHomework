package com.etaris.astrahomework

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** What the update check currently knows, rendered without blocking the page. */
data class AboutUpdateState(
    val checking: Boolean = false,
    val release: ReleaseInfo? = null,
    val error: String? = null,
    val checkedVersion: String? = null,
)

/**
 * Project information and the GitHub update check.
 *
 * Layout is a single scrolling column capped at 640dp so it reads comfortably on
 * phones and stays centred on tablets. The current version is always shown even
 * when the network is unavailable.
 *
 * @param update Current update-check result, including in-flight state.
 * @param insets Window insets forwarded from the scaffold.
 * @param onCheck Re-runs the GitHub check.
 * @param onOpen Opens an HTTPS link in the browser.
 */
@Composable
fun AboutScreen(
    update: AboutUpdateState,
    insets: PaddingValues,
    onCheck: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val direction = LocalLayoutDirection.current
    val repository = BuildConfig.GITHUB_REPO
    LazyColumn(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxSize().consumeWindowInsets(insets),
        contentPadding = PaddingValues(
            start = insets.calculateStartPadding(direction) + 24.dp,
            end = insets.calculateEndPadding(direction) + 24.dp,
            top = insets.calculateTopPadding() + 24.dp,
            bottom = insets.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_pdf),
                        contentDescription = null,
                        modifier = Modifier.padding(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column {
                    Text(
                        "AstraHomework",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "班级白板作业助手",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("版本", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "把白板 PDF 上传到自建的 AstrBot 服务，由服务端识别作业并推送总结。应用只保存服务器地址、访问令牌和文件夹权限。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            UpdateCard(update, onCheck, onOpen)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "项目信息",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                    )
                    LinkRow("GitHub 仓库", "github.com/$repository") { onOpen("https://github.com/$repository") }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    LinkRow("问题反馈", "提交 Issue") { onOpen("https://github.com/$repository/issues") }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    LinkRow("配套插件", "AstrBot 插件仓库") {
                        onOpen("https://github.com/$repository#服务端插件")
                    }
                    Text(
                        "本应用与服务端均不收集个人数据；令牌仅保存在本机，用于访问你配置的服务器。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
        item {
            Text(
                "Made with Jetpack Compose · Material 3 Expressive",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One tappable row in the project-information card. */
@Composable
private fun LinkRow(title: String, detail: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            TextButton(onClick = onClick) { Text("打开") }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Update state, its action, and the release notes when a newer version exists. */
@Composable
private fun UpdateCard(update: AboutUpdateState, onCheck: () -> Unit, onOpen: (String) -> Unit) {
    val release = update.release
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (release != null) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().animateContentSize(animationSpec = spring()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("检查更新", style = MaterialTheme.typography.titleMedium)
            when {
                update.checking -> Text("正在查询 GitHub 最新版本…")
                release != null -> {
                    Text(
                        "发现新版本 ${release.displayVersion}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (release.notes.isNotBlank()) {
                        Text(
                            release.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (release.apkUrl != null) {
                            Button(onClick = { onOpen(release.apkUrl) }) { Text("下载安装包") }
                        }
                        OutlinedButton(onClick = { onOpen(release.pageUrl) }) { Text("查看发布页") }
                    }
                }
                update.error != null -> Text(update.error)
                update.checkedVersion != null -> Text("已是最新版本")
                else -> Text("从 GitHub Releases 获取最新版本与更新说明。")
            }
            if (!update.checking) {
                Spacer(Modifier.width(0.dp))
                TextButton(onClick = onCheck) { Text(if (release != null) "重新检查" else "立即检查") }
            }
        }
    }
}
