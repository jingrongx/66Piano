package com.pianokids.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.pianokids.ui.components.StarCounter
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.PinkSoft
import com.pianokids.ui.theme.Primary
import com.pianokids.ui.theme.Secondary
import com.pianokids.ui.theme.Tertiary
import com.pianokids.ui.theme.YellowLight
import com.pianokids.ui.util.WindowClass
import com.pianokids.ui.util.rememberWindowClass

/**
 * 首页：问候语 + 豆豆头像 + 今日任务 + 星星 + 打卡 + 最近练习 + 闯关入口 + 家长区入口。
 *
 * @param onNavigateLearn 点击"今日任务"或"开始学习"时回调
 * @param onNavigatePractice 点击最近练习曲目时回调
 * @param onNavigateParent 点击家长区入口时回调
 * @param onNavigateChallenge 点击闯关大冒险入口时回调
 */
@Composable
fun HomeScreen(
    onNavigateLearn: () -> Unit,
    onNavigatePractice: () -> Unit,
    onNavigateParent: () -> Unit,
    onNavigateChallenge: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val totalStars by viewModel.totalStars.collectAsStateWithLifecycle()
    val streakDays by viewModel.streakDays.collectAsStateWithLifecycle()
    val petLevel by viewModel.petLevel.collectAsStateWithLifecycle()

    val greeting = viewModel.greeting()
    val petEmoji = viewModel.petEmoji(petLevel)

    val windowClass = rememberWindowClass()
    // 横屏 + MEDIUM/EXPANDED 时使用双列，充分利用横向空间
    val useTwoColumns = windowClass != WindowClass.COMPACT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = if (windowClass == WindowClass.COMPACT) 16.dp else 24.dp)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (windowClass == WindowClass.COMPACT) 16.dp else 12.dp),
    ) {
        // 顶部：问候语 + 豆豆头像
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "和豆豆一起学钢琴",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 豆豆头像
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(YellowLight, Secondary),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = petEmoji, fontSize = 40.sp)
            }
        }

        if (useTwoColumns) {
            // 横屏：左右两列，充分利用上部空间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 左列：今日任务 + 数据
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TodayTaskCard(onNavigateLearn)
                    StatsRow(totalStars, streakDays)
                }
                // 右列：最近练习 + 闯关入口 + 家长区入口
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RecentPracticeCard(onNavigatePractice)
                    ChallengeCard(onNavigateChallenge)
                    PetTipCard(petEmoji)
                    ParentEntryCard(onNavigateParent)
                }
            }
        } else {
            // 竖屏：单列
            TodayTaskCard(onNavigateLearn)
            StatsRow(totalStars, streakDays)
            RecentPracticeCard(onNavigatePractice)
            ChallengeCard(onNavigateChallenge)
            PetTipCard(petEmoji)
            ParentEntryCard(onNavigateParent)
        }
    }
}

/**
 * 闯关大冒险入口卡片。
 */
@Composable
private fun ChallengeCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Correct),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "闯关大冒险",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "听音 · 节奏 · 视奏三重挑战",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "挑战赢取星星 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.VideogameAsset,
                    contentDescription = "闯关",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * 家长区入口卡片。
 */
@Composable
private fun ParentEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "家长区",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 今日任务卡片：点击跳转到学习。
 */
@Composable
private fun TodayTaskCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "今日任务",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "认识钢琴 · 第 1 课",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "点击开始学习 →",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = "学习",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

/**
 * 星星数 + 连续打卡天数 数据行。
 */
@Composable
private fun StatsRow(totalStars: Int, streakDays: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 星星卡
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "星星",
                    tint = Secondary,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = totalStars.toString(),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "我的星星",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 打卡卡
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = "打卡",
                    tint = Correct,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$streakDays 天",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "连续打卡",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 最近练习曲目卡片。
 */
@Composable
private fun RecentPracticeCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Tertiary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "练习",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "最近练习",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    text = "小星星",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "点击继续练习 →",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * 豆豆小提示卡片。
 */
@Composable
private fun PetTipCard(petEmoji: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PinkSoft.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = petEmoji, fontSize = 40.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "豆豆在等你练习呢！",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
