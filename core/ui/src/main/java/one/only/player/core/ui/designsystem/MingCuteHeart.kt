// 图标来源：https://github.com/mingcute-design/mingcute-icons/blob/master/assets/svg/core/regular/shapes/heart.svg
// 路径与上游资源保持一致。
package one.only.player.core.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val MingCuteHeart: ImageVector by lazy {
    ImageVector.Builder(
        name = "MingCute.Heart",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString("M12 5.70537C8.30844 1.75781 2.88645 5.22688 3.00181 10.3707C3.07045 13.4312 5.46694 16.3647 10.1913 19.1711C10.4665 19.3345 10.8984 19.5805 11.2712 19.7909C11.7237 20.0464 12.2763 20.0464 12.7288 19.7909C13.1016 19.5805 13.5335 19.3345 13.8087 19.1711C18.5331 16.3647 20.9295 13.4312 20.9982 10.3707C21.1136 5.22688 15.6916 1.75781 12 5.70537Z").toNodes(),
            pathFillType = PathFillType.NonZero,
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()
}
