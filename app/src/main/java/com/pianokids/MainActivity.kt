package com.pianokids

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.pianokids.ui.PianoKidsApp
import com.pianokids.ui.theme.PianoKidsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用唯一 Activity。
 *
 * **权限策略**：启动时强制要求所有必须权限（麦克风、相机）。
 * 用户必须全部授权才能进入主界面；拒绝则显示权限说明卡片并引导到系统设置。
 *
 * 必须权限：
 * - RECORD_AUDIO：识别钢琴音准（核心功能）
 * - CAMERA：拍照识谱/导入乐谱
 * - （INTERNET / ACCESS_NETWORK_STATE 是 normal 权限，自动授予）
 * - （REQUEST_INSTALL_PACKAGES 是 signature 权限，由系统安装器调起时检查）
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PianoKidsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PermissionGate(
                        requiredPermissions = RequiredPermissions,
                        content = { PianoKidsApp() },
                    )
                }
            }
        }
    }

    companion object {
        /** 必须运行时申请的权限列表。 */
        private val RequiredPermissions: Array<String> = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
        }.toTypedArray()
    }
}

/**
 * 权限门控 Composable：检查所有必须权限是否已授予，
 * 未授予则展示说明卡片并触发申请；用户拒绝后展示"前往设置"按钮。
 *
 * 作为顶级函数定义在文件顶层（非 MainActivity 内部），
 * 避免 kapt 处理 Activity 时无法解析 @Composable 注解。
 */
@Composable
fun PermissionGate(
    requiredPermissions: Array<String>,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var allGranted by remember {
        mutableStateOf(checkAllPermissions(context, requiredPermissions))
    }
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        allGranted = result.values.all { it }
        showRationale = !allGranted && requiredPermissions.any {
            // 在 Composable 中无法直接调用 shouldShowRequestPermissionRationale，
            // 通过 ContextWrapper 取到 Activity
            (context as? android.app.Activity)?.shouldShowRequestPermissionRationale(it) == true
        }
    }

    LaunchedEffect(Unit) {
        if (!allGranted) {
            launcher.launch(requiredPermissions)
        }
    }

    if (allGranted) {
        content()
    } else {
        PermissionRequiredScreen(
            showRationale = showRationale,
            onRequestAgain = { launcher.launch(requiredPermissions) },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
        )
    }
}

private fun checkAllPermissions(
    context: Context,
    permissions: Array<String>,
): Boolean = permissions.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PermissionRequiredScreen(
    showRationale: Boolean,
    onRequestAgain: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "需要权限才能使用",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (showRationale) {
                        "钢琴学院需要麦克风识别音准、相机拍照识谱。\n您上次拒绝了权限，请到系统设置手动授予后返回。"
                    } else {
                        "钢琴学院需要以下权限：\n• 麦克风：识别钢琴音准\n• 相机：拍照识谱/导入乐谱\n\n请点击下方按钮授予权限。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (showRationale) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(imageVector = Icons.Filled.Mic, contentDescription = null)
                        Text("  前往系统设置", color = Color.White)
                    }
                } else {
                    Button(
                        onClick = onRequestAgain,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("授予权限", color = Color.White)
                    }
                }
            }
        }
    }
}
