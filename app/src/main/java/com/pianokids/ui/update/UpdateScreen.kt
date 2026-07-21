package com.pianokids.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianokids.update.UpdateUiState
import com.pianokids.update.UpdateViewModel
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.PinkSoft
import com.pianokids.ui.theme.Primary
import com.pianokids.ui.theme.Secondary
import com.pianokids.ui.theme.Tertiary
import com.pianokids.ui.theme.YellowLight

/**
 * 应用更新页面。
 *
 * - 进入即自动检测更新
 * - 显示当前版本与远端最新版本
 * - 支持手动点击"重新检测"
 * - 支持切换 ghproxy 加速开关
 * - 支持下载并调起系统安装器
 *
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("应用更新", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.checkForUpdate() },
                        enabled = !uiState.checking && !uiState.downloading,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "重新检测",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. 当前版本卡片
            CurrentVersionCard(
                versionName = uiState.currentVersionName,
                versionCode = uiState.currentVersionCode,
            )

            // 2. 加速开关
            ProxyToggleCard(
                useProxy = uiState.useProxy,
                onToggle = { viewModel.toggleProxy() },
            )

            // 3. 检测结果 / 最新版本信息
            if (uiState.checking) {
                CheckingCard()
            } else {
                LatestVersionCard(state = uiState)
            }

            // 4. 下载进度（如果正在下载）
            if (uiState.downloading) {
                DownloadCard(progress = uiState.downloadProgress)
            }

            // 5. 操作按钮
            ActionButtons(
                state = uiState,
                onCheck = { viewModel.checkForUpdate() },
                onDownload = { viewModel.downloadAndInstall() },
            )
        }
    }
}

@Composable
private fun CurrentVersionCard(versionName: String, versionCode: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(YellowLight, Secondary, PinkSoft),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "versionCode: $versionCode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProxyToggleCard(
    useProxy: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = null,
                tint = if (useProxy) Tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ghproxy 加速",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (useProxy) "已启用国内加速" else "直连 GitHub（国内可能慢）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = useProxy,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun CheckingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = Primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "正在检测更新…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun LatestVersionCard(state: UpdateUiState) {
    val info = state.latest
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.hasUpdate) Correct.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.hasUpdate) Icons.Filled.CloudDownload else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (state.hasUpdate) Correct else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (state.hasUpdate) "发现新版本" else "已是最新版本",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (info != null) {
                Text(
                    text = "最新版本：v${info.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "发布时间：${info.publishedAt}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.hasUpdate && info.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "更新内容",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (!state.checking) {
                Text(
                    text = "无法获取最新版本信息，请检查网络或切换加速开关后重试",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DownloadCard(progress: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "正在下载新版本…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionButtons(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCheck,
            enabled = !state.checking && !state.downloading,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("重新检测")
        }
        Button(
            onClick = onDownload,
            enabled = state.hasUpdate && !state.checking && !state.downloading && state.latest != null,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("下载并安装", color = Color.White, fontSize = 14.sp)
        }
    }
}
