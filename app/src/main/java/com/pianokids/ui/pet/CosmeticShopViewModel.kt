package com.pianokids.ui.pet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pianokids.data.prefs.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 单个装扮项的展示状态。
 */
data class CosmeticItemState(
    val cosmetic: Cosmetic,
    val owned: Boolean,
    val equipped: Boolean,
)

/**
 * 装扮商店 UI 状态。
 *
 * @property items 所有装扮的展示状态
 * @property stars 当前星星数
 * @property loading 是否加载中
 * @property message 一次性消息（购买成功/失败、装备/卸下提示）
 */
data class CosmeticShopUiState(
    val items: List<CosmeticItemState> = emptyList(),
    val stars: Int = 0,
    val loading: Boolean = true,
    val message: String? = null,
)

/**
 * 装扮商店 ViewModel。
 *
 * 支持：
 * - 列出全部装扮 + 拥有/装备状态
 * - 购买装扮（扣星星）
 * - 装备/卸下装扮
 */
@HiltViewModel
class CosmeticShopViewModel @Inject constructor(
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CosmeticShopUiState())
    val uiState: StateFlow<CosmeticShopUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            refresh()
        }
    }

    /**
     * 刷新状态。
     */
    fun refresh() {
        viewModelScope.launch {
            val owned = userPreferences.ownedCosmetics()
            val equipped = userPreferences.equippedCosmetics()
            val stars = userPreferences.totalStars()
            val items = CosmeticCatalog.all.map { c ->
                CosmeticItemState(
                    cosmetic = c,
                    owned = c.id in owned,
                    equipped = c.id in equipped,
                )
            }
            _uiState.value = CosmeticShopUiState(items = items, stars = stars, loading = false)
        }
    }

    /**
     * 购买某装扮。
     */
    fun purchase(cosmetic: Cosmetic) {
        viewModelScope.launch {
            val ok = userPreferences.purchaseCosmetic(cosmetic.id, cosmetic.cost)
            _uiState.update {
                it.copy(message = if (ok) "购买成功！${cosmetic.name} 已加入背包" else "星星不足，还差 ${cosmetic.cost - it.stars} 颗")
            }
            refresh()
        }
    }

    /**
     * 装备/卸下某装扮。
     */
    fun toggleEquip(cosmetic: Cosmetic) {
        viewModelScope.launch {
            val currentlyEquipped = userPreferences.equippedCosmetics().contains(cosmetic.id)
            userPreferences.toggleEquippedCosmetic(cosmetic.id, !currentlyEquipped)
            _uiState.update {
                it.copy(message = if (currentlyEquipped) "已卸下 ${cosmetic.name}" else "已装备 ${cosmetic.name}")
            }
            refresh()
        }
    }

    /**
     * 消费一次性消息。
     */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
