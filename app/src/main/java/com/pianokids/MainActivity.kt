package com.pianokids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.pianokids.ui.PianoKidsApp
import com.pianokids.ui.theme.PianoKidsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 应用唯一 Activity，承载 Compose 导航。
 * 横屏使用，钢琴在下方，屏幕在上方。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PianoKidsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PianoKidsApp()
                }
            }
        }
    }
}
