package one.only.player.core.model

// 尺寸只影响 HyperOS 4 底栏，切换样式时保留用户设置。
object HyperOsBottomBarDefaults {
    const val DEFAULT_WIDTH_PERCENT = 100
    const val DEFAULT_HEIGHT_DP = 54
    val widthPercentRange = 70..100
    val heightDpRange = 54..88
}
