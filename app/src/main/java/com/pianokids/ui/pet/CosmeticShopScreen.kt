package com.pianokids.ui.pet

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pianokids.ui.theme.Correct
import com.pianokids.ui.theme.Primary
import com.pianokids.ui.theme.Secondary

/**
 * 装扮商店界面。
 *
 * 网格展示所有装扮，每件可：
 * - 未拥有：显示价格，点击购买
 * - 已拥有未装备：显示「装备」按钮
 * - 已装备：显示「卸下」按钮 + 已装备标识
 */
@Composable
fun CosmeticShopScreen(
    onBack: () -> Unit,
    viewModel: CosmeticShopViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingPurchase by remember { mutableStateOf<Cosmetic?>(null) }

    // 一次性消息显示为 snackbar
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 顶部
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "装扮商店",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // 星星
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Secondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = uiState.stars.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                text = "用星星给豆豆买装扮吧~",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 装扮网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.items, key = { it.cosmetic.id }) { item ->
                    CosmeticCard(
                        item = item,
                        stars = uiState.stars,
                        onPurchase = { pendingPurchase = item.cosmetic },
                        onToggleEquip = { viewModel.toggleEquip(item.cosmetic) },
                    )
                }
            }
        }
    }

    // 购买确认对话框
    pendingPurchase?.let { cosmetic ->
        AlertDialog(
            onDismissRequest = { pendingPurchase = null },
            title = { Text("购买 ${cosmetic.name}") },
            text = {
                Text("花费 ${cosmetic.cost} 颗星购买「${cosmetic.emoji} ${cosmetic.name}」？\n${cosmetic.description}")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.purchase(cosmetic)
                    pendingPurchase = null
                }) {
                    Text("购买", color = Primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurchase = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun CosmeticCard(
    item: CosmeticItemState,
    stars: Int,
    onPurchase: () -> Unit,
    onToggleEquip: () -> Unit,
) {
    val cosmetic = item.cosmetic
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 装扮 emoji
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(cosmetic.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = cosmetic.emoji, fontSize = 40.sp)
            }

            Text(
                text = cosmetic.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = cosmetic.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 操作按钮
            if (!item.owned) {
                // 未拥有：显示价格 + 购买
                Button(
                    onClick = onPurchase,
                    enabled = stars >= cosmetic.cost,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${cosmetic.cost}", color = Color.White)
                }
            } else if (item.equipped) {
                // 已装备：显示卸下
                OutlinedButton(
                    onClick = onToggleEquip,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Correct, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("已装备")
                }
            } else {
                // 已拥有未装备：显示装备
                Button(
                    onClick = onToggleEquip,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Correct),
                ) {
                    Text("装备", color = Color.White)
                }
            }
        }
    }
}
