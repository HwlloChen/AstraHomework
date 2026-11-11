package com.etaris.astrahomework

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/** True when this build carries the CI "-ci.N" version suffix. */
private val isCiBuild get() = BuildConfig.VERSION_NAME.contains("-ci")

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
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { AboutHero() }
        item { VersionCard() }
        item { UpdateCard(update, onCheck, onOpen) }
        item { ProjectInfoCard(repository, onOpen) }
        item { PrivacyCard() }
        item { Footer() }
    }
}

/** Brand identity: icon, name, and a one-line purpose. */
@Composable
private fun AboutHero() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_pdf),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "AstraHomework",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "班级白板作业助手",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Current version with a badge distinguishing CI builds from stable releases. */
@Composable
private fun VersionCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "当前版本",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (isCiBuild) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "CI 构建",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "稳定版",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Update check: status row on the left, action button on the right.
 *
 * The compact status line keeps the card from jumping as results arrive, and the
 * whole layout only expands when there is actually a release to show.
 */
@Composable
private fun UpdateCard(update: AboutUpdateState, onCheck: () -> Unit, onOpen: (String) -> Unit) {
    val release = update.release
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (release != null) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            Modifier.fillMaxWidth().animateContentSize(animationSpec = spring()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: title + inline status on one side, the action on the other.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("检查更新", style = MaterialTheme.typography.titleMedium)
                    UpdateStatusLine(update, release)
                }
                CheckButton(update = update, hasRelease = release != null, onCheck = onCheck)
            }
            // Expanded body only when a newer release exists.
            AnimatedVisibility(visible = release != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val info = release
                    if (info != null) {
                        HorizontalRule()
                        Text(
                            "新版本 ${info.displayVersion}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (info.notes.isNotBlank()) {
                            Text(
                                info.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (info.apkUrl != null) {
                                Button(onClick = { onOpen(info.apkUrl) }) { Text("下载安装包") }
                            }
                            OutlinedButton(onClick = { onOpen(info.pageUrl) }) { Text("查看发布页") }
                        }
                    }
                }
            }
        }
    }
}

/** Compact one-line status under the card title. */
@Composable
private fun UpdateStatusLine(update: AboutUpdateState, release: ReleaseInfo?) {
    val text = when {
        update.checking -> "正在查询 GitHub…"
        release != null -> "发现新版本"
        update.error != null -> update.error
        update.checkedVersion != null -> "已是最新版本"
        else -> "从 GitHub Releases 获取更新"
    }
    val color = when {
        update.checking -> MaterialTheme.colorScheme.onSurfaceVariant
        release != null -> MaterialTheme.colorScheme.onTertiaryContainer
        update.error != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** Compact circular action button while checking, tonal otherwise. */
@Composable
private fun CheckButton(update: AboutUpdateState, hasRelease: Boolean, onCheck: () -> Unit) {
    if (update.checking) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
    } else {
        FilledTonalButton(onClick = onCheck) {
            Text(if (hasRelease) "重新检查" else "立即检查")
        }
    }
}

/** Thin separator that inherits the local content color. */
@Composable
private fun HorizontalRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** GitHub repository, issue tracker, and plugin links. */
@Composable
private fun ProjectInfoCard(repository: String, onOpen: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "项目信息",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
            )
            LinkRow(
                icon = R.drawable.ic_code,
                title = "GitHub 仓库",
                detail = "github.com/$repository",
                onClick = { onOpen("https://github.com/$repository") },
            )
            LinkRow(
                icon = R.drawable.ic_bug,
                title = "问题反馈",
                detail = "提交 Issue",
                onClick = { onOpen("https://github.com/$repository/issues") },
            )
            LinkRow(
                icon = R.drawable.ic_link,
                title = "配套插件",
                detail = "AstrBot 插件仓库",
                onClick = { onOpen("https://github.com/$repository#服务端插件") },
            )
        }
    }
}

/** Privacy statement. */
@Composable
private fun PrivacyCard() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_privacy),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Text(
                "本应用与服务端均不收集个人数据；令牌仅保存在本机，用于访问你配置的服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One tappable row in the project-information card. */
@Composable
private fun LinkRow(
    icon: Int,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painterResource(R.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bottom attribution. */
@Composable
private fun Footer() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Made with Jetpack Compose",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Material 3 Expressive",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
