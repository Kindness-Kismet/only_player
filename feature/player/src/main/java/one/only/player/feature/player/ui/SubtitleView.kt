package one.only.player.feature.player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.CaptioningManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat.getSystemService
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.widget.AssSubtitleView as AssMediaSubtitleView
import one.only.player.core.data.repository.ExternalSubtitleFontSource
import one.only.player.core.model.Font
import one.only.player.core.model.PlayerPreferences
import one.only.player.core.model.SubtitleColor
import one.only.player.core.model.SubtitleEdgeStyle
import one.only.player.feature.player.extensions.toTypeface
import one.only.player.feature.player.state.rememberCuesState
import one.only.player.feature.player.state.rememberTracksState
import one.only.player.feature.player.subtitle.AssHandlerRegistry
import one.only.player.feature.player.subtitle.SubtitleFontPolicy
import one.only.player.feature.player.subtitle.decideSubtitleFontPolicy

@OptIn(UnstableApi::class)
@Composable
fun SubtitleView(
    modifier: Modifier = Modifier,
    player: Player,
    isInPictureInPictureMode: Boolean,
    configuration: SubtitleConfiguration,
) {
    val cuesState = rememberCuesState(player)
    val assHandler by AssHandlerRegistry.handler.collectAsState()
    val textTracksState = rememberTracksState(player = player, trackType = C.TRACK_TYPE_TEXT)
    val isAssSubtitleSelected = textTracksState.tracks.any { track ->
        track.isSelected &&
            (0 until track.mediaTrackGroup.length).any { index ->
                val format = track.mediaTrackGroup.getFormat(index)
                format.sampleMimeType == MimeTypes.TEXT_SSA || format.codecs == MimeTypes.TEXT_SSA
            }
    }

    val subtitleFontPolicy = decideSubtitleFontPolicy(
        isAssSubtitleSelected = isAssSubtitleSelected,
        shouldUseSystemCaptionStyle = configuration.shouldUseSystemCaptionStyle,
        hasExternalFont = configuration.externalSubtitleFontSource != null,
    )

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SubtitleContainer(context).apply {
                subtitleView.applyDefaultSubtitleStyle()
            }
        },
        update = { container ->
            val subtitleView = container.subtitleView
            val shouldUseTextSubtitleStyle = !isAssSubtitleSelected && cuesState.cues.canUseTextSubtitleStyle()
            val shouldUseAdvancedTextSubtitle = shouldUseTextSubtitleStyle &&
                subtitleFontPolicy == SubtitleFontPolicy.ExternalOrFallback &&
                configuration.shouldUseAdvancedEdgeStyle()

            if (shouldUseTextSubtitleStyle) {
                subtitleView.applySubtitleStyle(
                    configuration = configuration,
                    subtitleFontPolicy = subtitleFontPolicy,
                )
                subtitleView.setApplyEmbeddedStyles(configuration.shouldApplyEmbeddedStyles)
                subtitleView.applySubtitlePosition(
                    configuration = configuration,
                    subtitleFontPolicy = subtitleFontPolicy,
                )

                if (isInPictureInPictureMode) {
                    subtitleView.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
                } else {
                    subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, configuration.textSize)
                }
            } else {
                subtitleView.applyDefaultSubtitleStyle()
            }

            if (isAssSubtitleSelected) {
                assHandler?.let(subtitleView::syncAssSupport)
                    ?: subtitleView.clearAssSupport()
                subtitleView.setCues(emptyList())
                container.advancedSubtitleView.visibility = View.GONE
            } else {
                subtitleView.clearAssSupport()
                subtitleView.setCues(cuesState.cues.takeUnless { shouldUseAdvancedTextSubtitle }.orEmpty())
                container.applyAdvancedSubtitle(
                    cues = cuesState.cues,
                    configuration = configuration,
                    isVisible = shouldUseAdvancedTextSubtitle,
                    isInPictureInPictureMode = isInPictureInPictureMode,
                )
            }
        },
    )
}

@Stable
data class SubtitleConfiguration(
    val shouldUseSystemCaptionStyle: Boolean,
    val shouldShowBackground: Boolean,
    val font: Font,
    val textSize: Float,
    val shouldUseBoldText: Boolean,
    val color: SubtitleColor,
    val edgeStyle: SubtitleEdgeStyle,
    val outlineThickness: Float,
    val shadowStrength: Float,
    val bottomPaddingFraction: Float,
    val shouldApplyEmbeddedStyles: Boolean,
    val externalSubtitleFontSource: ExternalSubtitleFontSource?,
)

private class SubtitleContainer(context: Context) : FrameLayout(context) {
    val subtitleView = SubtitleView(context)
    val advancedSubtitleView = AdvancedSubtitleTextView(context)

    init {
        addView(subtitleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(advancedSubtitleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}

private class AdvancedSubtitleTextView(context: Context) : TextView(context) {
    var outlineThickness: Float = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    var hasOutline: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        gravity = Gravity.CENTER or Gravity.BOTTOM
        includeFontPadding = false
        textAlignment = TEXT_ALIGNMENT_CENTER
        visibility = View.GONE
    }

    override fun onDraw(canvas: Canvas) {
        if (hasOutline && outlineThickness > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = outlineThickness
            paint.color = Color.BLACK
            drawTextLayout(canvas)
        }

        paint.style = Paint.Style.FILL
        paint.color = currentTextColor
        super.onDraw(canvas)
    }

    private fun drawTextLayout(canvas: Canvas) {
        val layout = layout ?: return
        val contentHeight = height - compoundPaddingTop - compoundPaddingBottom
        val verticalOffset = (contentHeight - layout.height).coerceAtLeast(0)
        canvas.save()
        canvas.translate(
            compoundPaddingLeft.toFloat(),
            (compoundPaddingTop + verticalOffset).toFloat(),
        )
        layout.draw(canvas)
        canvas.restore()
    }
}

@OptIn(UnstableApi::class)
private fun SubtitleContainer.applyAdvancedSubtitle(
    cues: List<Cue>,
    configuration: SubtitleConfiguration,
    isVisible: Boolean,
    isInPictureInPictureMode: Boolean,
) {
    if (!isVisible) {
        advancedSubtitleView.visibility = View.GONE
        return
    }

    val shadowStrength = configuration.shadowStrength.takeIf { configuration.edgeStyle.hasShadow() } ?: 0f
    val textSizeUnit = if (isInPictureInPictureMode) {
        TypedValue.COMPLEX_UNIT_PX
    } else {
        TypedValue.COMPLEX_UNIT_SP
    }
    val textSize = if (isInPictureInPictureMode) {
        height.takeIf { it > 0 }
            ?.let { it * SubtitleView.DEFAULT_TEXT_SIZE_FRACTION }
            ?: (configuration.textSize * advancedSubtitleView.resources.displayMetrics.scaledDensity)
    } else {
        configuration.textSize
    }
    val baseTypeface = configuration.resolveTypeface()
    val cueText = cues.first().text
    val subtitleText = if (configuration.shouldApplyEmbeddedStyles) {
        cueText ?: ""
    } else {
        cueText?.toString().orEmpty()
    }
    advancedSubtitleView.visibility = View.VISIBLE
    advancedSubtitleView.text = subtitleText.withBackground(configuration.shouldShowBackground)
    advancedSubtitleView.setTextColor(configuration.color.toArgb())
    advancedSubtitleView.setTextSize(textSizeUnit, textSize)
    advancedSubtitleView.typeface = Typeface.create(
        baseTypeface,
        Typeface.BOLD.takeIf { configuration.shouldUseBoldText } ?: Typeface.NORMAL,
    )
    advancedSubtitleView.setPadding(0, 0, 0, (height * configuration.bottomPaddingFraction).toInt())
    advancedSubtitleView.setBackgroundColor(Color.TRANSPARENT)
    advancedSubtitleView.hasOutline = configuration.edgeStyle.hasOutline()
    advancedSubtitleView.outlineThickness = configuration.outlineThickness
    advancedSubtitleView.paint.strokeJoin = Paint.Join.ROUND
    advancedSubtitleView.setShadowLayer(shadowStrength, shadowStrength, shadowStrength, Color.BLACK)
    advancedSubtitleView.invalidate()
}

@OptIn(UnstableApi::class)
private fun SubtitleView.syncAssSupport(handler: AssHandler) {
    val assSupportView = findAssSupportView()
    val currentHandler = assSupportView?.tag as? AssHandler
    if (currentHandler === handler) return

    assSupportView?.let(::removeView)
    this.withAssSupport(handler)
    findAssSupportView()?.tag = handler
}

private fun SubtitleView.clearAssSupport() {
    findAssSupportView()?.let(::removeView)
}

private fun SubtitleView.applySubtitleStyle(
    configuration: SubtitleConfiguration,
    subtitleFontPolicy: SubtitleFontPolicy,
) {
    when (subtitleFontPolicy) {
        SubtitleFontPolicy.Ass,
        SubtitleFontPolicy.SystemCaptionStyle,
        -> applyDefaultSubtitleStyle()

        SubtitleFontPolicy.ExternalOrFallback -> {
            val baseTypeface = configuration.resolveTypeface()
            val userStyle = CaptionStyleCompat(
                configuration.color.toArgb(),
                Color.BLACK.takeIf { configuration.shouldShowBackground } ?: Color.TRANSPARENT,
                Color.TRANSPARENT,
                configuration.edgeStyle.toCaptionEdgeType(),
                Color.BLACK,
                Typeface.create(
                    baseTypeface,
                    Typeface.BOLD.takeIf { configuration.shouldUseBoldText } ?: Typeface.NORMAL,
                ),
            )
            setStyle(userStyle)
            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, configuration.textSize)
        }
    }
}

private fun SubtitleView.applyDefaultSubtitleStyle() {
    val context = context
    val captioningManager = getSystemService(context, CaptioningManager::class.java) ?: return
    val systemCaptionStyle = CaptionStyleCompat.createFromCaptionStyle(captioningManager.userStyle)
    setStyle(systemCaptionStyle)
    setApplyEmbeddedStyles(true)
    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION)
    setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
}

@OptIn(UnstableApi::class)
private fun SubtitleView.applySubtitlePosition(
    configuration: SubtitleConfiguration,
    subtitleFontPolicy: SubtitleFontPolicy,
) {
    val bottomPaddingFraction = when (subtitleFontPolicy) {
        SubtitleFontPolicy.ExternalOrFallback -> configuration.bottomPaddingFraction
        SubtitleFontPolicy.Ass,
        SubtitleFontPolicy.SystemCaptionStyle,
        -> SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
    }
    setBottomPaddingFraction(bottomPaddingFraction)
}

private fun SubtitleConfiguration.resolveTypeface(): Typeface = runCatching {
    externalSubtitleFontSource
        ?.absolutePath
        ?.let(Typeface::createFromFile)
}.getOrNull() ?: font.toTypeface()

private fun SubtitleColor.toArgb(): Int = when (this) {
    SubtitleColor.WHITE -> Color.WHITE
    SubtitleColor.YELLOW -> Color.YELLOW
    SubtitleColor.CYAN -> Color.CYAN
    SubtitleColor.GREEN -> Color.GREEN
}

private fun SubtitleEdgeStyle.toCaptionEdgeType(): Int = when (this) {
    SubtitleEdgeStyle.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
    SubtitleEdgeStyle.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
    SubtitleEdgeStyle.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
    SubtitleEdgeStyle.OUTLINE_AND_DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
}

private fun SubtitleConfiguration.shouldUseAdvancedEdgeStyle(): Boolean = when (edgeStyle) {
    SubtitleEdgeStyle.NONE -> false
    SubtitleEdgeStyle.OUTLINE -> outlineThickness != PlayerPreferences.DEFAULT_SUBTITLE_OUTLINE_THICKNESS
    SubtitleEdgeStyle.DROP_SHADOW -> shadowStrength != PlayerPreferences.DEFAULT_SUBTITLE_SHADOW_STRENGTH
    SubtitleEdgeStyle.OUTLINE_AND_DROP_SHADOW -> true
}

private fun List<Cue>.canUseTextSubtitleStyle(): Boolean = size == 1 && first().isDefaultTextCue()

private fun Cue.isDefaultTextCue(): Boolean = text != null &&
    bitmap == null &&
    textAlignment in setOf(null, Layout.Alignment.ALIGN_CENTER) &&
    multiRowAlignment == null &&
    line == Cue.DIMEN_UNSET &&
    lineType == Cue.TYPE_UNSET &&
    lineAnchor == Cue.TYPE_UNSET &&
    position == Cue.DIMEN_UNSET &&
    positionAnchor == Cue.TYPE_UNSET &&
    textSize == Cue.DIMEN_UNSET &&
    textSizeType == Cue.TYPE_UNSET &&
    size == Cue.DIMEN_UNSET &&
    bitmapHeight == Cue.DIMEN_UNSET &&
    !windowColorSet &&
    verticalType == Cue.TYPE_UNSET &&
    shearDegrees == 0f

private fun CharSequence.withBackground(shouldShowBackground: Boolean): CharSequence {
    if (!shouldShowBackground) return this
    return SpannableString(this).apply {
        setSpan(BackgroundColorSpan(Color.BLACK), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

private fun SubtitleEdgeStyle.hasOutline(): Boolean = this == SubtitleEdgeStyle.OUTLINE || this == SubtitleEdgeStyle.OUTLINE_AND_DROP_SHADOW

private fun SubtitleEdgeStyle.hasShadow(): Boolean = this == SubtitleEdgeStyle.DROP_SHADOW || this == SubtitleEdgeStyle.OUTLINE_AND_DROP_SHADOW

private fun SubtitleView.findAssSupportView(): AssMediaSubtitleView? = (0 until childCount).firstNotNullOfOrNull { index ->
    getChildAt(index) as? AssMediaSubtitleView
}
