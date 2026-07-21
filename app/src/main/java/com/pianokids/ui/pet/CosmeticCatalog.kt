package com.pianokids.ui.pet

import androidx.compose.ui.graphics.Color

/**
 * 装扮目录（静态数据）。
 *
 * @property id 唯一 id
 * @property name 装扮名
 * @property emoji 展示 emoji
 * @property cost 价格（星星）
 * @property category 分类（HAT / GLASSES / SCARF / BACKGROUND）
 * @property color 展示色
 * @property description 描述
 */
data class Cosmetic(
    val id: String,
    val name: String,
    val emoji: String,
    val cost: Int,
    val category: Category,
    val color: Color,
    val description: String,
) {
    enum class Category { HAT, GLASSES, SCARF, BACKGROUND, ACCESSORY }
}

/**
 * 全部装扮商品。
 *
 * P2.2 版本：8 件商品，价格 5~30 颗星。
 */
object CosmeticCatalog {

    val all: List<Cosmetic> = listOf(
        Cosmetic("hat_cap", "小帽子", "🎓", 5, Cosmetic.Category.HAT, Color(0xFFFF8A65), "戴上小帽子上学"),
        Cosmetic("hat_crown", "皇冠", "👑", 30, Cosmetic.Category.HAT, Color(0xFFFFD54F), "豆豆成为国王"),
        Cosmetic("hat_bow", "蝴蝶结", "🎀", 10, Cosmetic.Category.HAT, Color(0xFFF8BBD0), "可爱的蝴蝶结"),
        Cosmetic("glasses_sunglasses", "太阳镜", "🕶", 15, Cosmetic.Category.GLASSES, Color(0xFF212121), "酷酷的太阳镜"),
        Cosmetic("glasses_glasses", "眼镜", "👓", 8, Cosmetic.Category.GLASSES, Color(0xFF42A5F5), "聪明的眼镜"),
        Cosmetic("scarf_red", "红围巾", "🧣", 12, Cosmetic.Category.SCARF, Color(0xFFEF5350), "暖暖的红围巾"),
        Cosmetic("bg_stage", "舞台背景", "🎆", 25, Cosmetic.Category.BACKGROUND, Color(0xFF7E57C2), "华丽的舞台"),
        Cosmetic("acc_star", "星星徽章", "⭐", 20, Cosmetic.Category.ACCESSORY, Color(0xFFFFD54F), "闪亮的星星"),
    )

    fun get(id: String): Cosmetic? = all.firstOrNull { it.id == id }
}
