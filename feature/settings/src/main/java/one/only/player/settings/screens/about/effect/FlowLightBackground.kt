// SPDX-License-Identifier: GPL-3.0-only
package one.only.player.settings.screens.about.effect

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.shader.RuntimeShader
import top.yukonga.miuix.kmp.shader.asBrush
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FlowLightBackground(
    modifier: Modifier = Modifier,
    isAnimated: Boolean = true,
    isDarkTheme: Boolean = MiuixTheme.colorScheme.surface.luminance() < 0.5f,
    backgroundModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!isRuntimeShaderSupported()) {
        Box(modifier = modifier.background(MiuixTheme.colorScheme.surface), content = content)
        return
    }

    Box(modifier = modifier) {
        val surface = MiuixTheme.colorScheme.surface
        val painter = remember { FlowLightPainter() }
        val preset = remember(isDarkTheme) {
            if (isDarkTheme) OS3_PHONE_DARK else OS3_PHONE_LIGHT
        }
        val colorStage = remember { Animatable(0f) }

        LaunchedEffect(isAnimated, preset) {
            if (!isAnimated) return@LaunchedEffect
            var target = floor(colorStage.value) + 1f
            while (isActive) {
                colorStage.animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
                )
                target += 1f
            }
        }

        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundModifier)
                .flowLightDraw(
                    painter = painter,
                    preset = preset,
                    surface = surface,
                    isPlaying = isAnimated,
                    colorStage = { colorStage.value },
                ),
        )
        content()
    }
}

private class FlowLightConfig(
    val points: FloatArray,
    val colors1: FloatArray,
    val colors2: FloatArray,
    val colors3: FloatArray,
    val lightOffset: Float,
    val saturateOffset: Float,
    val pointOffset: Float,
)

private val OS3_POINTS = floatArrayOf(0.8f, 0.2f, 1.0f, 0.8f, 0.9f, 1.0f, 0.2f, 0.9f, 1.0f, 0.2f, 0.2f, 1.0f)

private val OS3_PHONE_LIGHT = FlowLightConfig(
    points = OS3_POINTS,
    colors1 = floatArrayOf(1.0f, 0.9f, 0.94f, 1.0f, 1.0f, 0.84f, 0.89f, 1.0f, 0.97f, 0.73f, 0.82f, 1.0f, 0.64f, 0.65f, 0.98f, 1.0f),
    colors2 = floatArrayOf(0.58f, 0.74f, 1.0f, 1.0f, 1.0f, 0.9f, 0.93f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.97f, 0.77f, 0.84f, 1.0f),
    colors3 = floatArrayOf(0.98f, 0.86f, 0.9f, 1.0f, 0.6f, 0.73f, 0.98f, 1.0f, 0.92f, 0.93f, 1.0f, 1.0f, 0.56f, 0.69f, 1.0f, 1.0f),
    lightOffset = 0.1f,
    saturateOffset = 0.2f,
    pointOffset = 0.2f,
)

private val OS3_PHONE_DARK = FlowLightConfig(
    points = OS3_POINTS,
    colors1 = floatArrayOf(0.2f, 0.06f, 0.88f, 0.4f, 0.3f, 0.14f, 0.55f, 0.5f, 0.0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.4f),
    colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.2f, 0.78f, 0.5f),
    colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f),
    lightOffset = 0.0f,
    saturateOffset = 0.17f,
    pointOffset = 0.4f,
)

private class FlowLightPainter {
    private val shader: RuntimeShader by lazy {
        RuntimeShader(OS3_BG_FRAG).also { current ->
            current.setFloatUniform("uTranslateY", 0f)
            current.setFloatUniform("uNoiseScale", 1.5f)
            current.setFloatUniform("uPointRadiusMulti", 1f)
            current.setFloatUniform("uAlphaMulti", 1f)
        }
    }

    val brush: Brush get() = shader.asBrush()

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private val colorsBuffer = FloatArray(16)
    private val pointsAnimBuffer = FloatArray(8)
    private var animTime = Float.NaN
    private var presetApplied: FlowLightConfig? = null
    private var cachedHeight = Float.NaN
    private var cachedWidth = Float.NaN
    private var cachedColorStage = Float.NaN
    private var cachedColorsPreset: FlowLightConfig? = null
    private var cachedPointsTime = Float.NaN
    private var cachedPointsPreset: FlowLightConfig? = null

    fun updateResolution(
        width: Float,
        height: Float,
    ) {
        if (resolution[0] == width && resolution[1] == height) return
        resolution[0] = width
        resolution[1] = height
        shader.setFloatUniform("uResolution", resolution)
    }

    fun updateAnimTime(time: Float) {
        if (animTime == time) return
        animTime = time
        shader.setFloatUniform("uAnimTime", animTime)
    }

    fun updatePointsAnim(
        time: Float,
        preset: FlowLightConfig,
    ) {
        if (cachedPointsTime == time && cachedPointsPreset === preset) return
        val offset = preset.pointOffset
        for (i in 0 until 4) {
            val srcX = preset.points[i * 3]
            val srcY = preset.points[i * 3 + 1]
            val animX = srcX + sin(time + srcY) * offset
            val animY = srcY + cos(time + animX) * offset
            pointsAnimBuffer[i * 2] = animX
            pointsAnimBuffer[i * 2 + 1] = animY
        }
        shader.setFloatUniform("uPointsAnim", pointsAnimBuffer)
        cachedPointsTime = time
        cachedPointsPreset = preset
    }

    fun updateColors(
        preset: FlowLightConfig,
        stage: Float,
    ) {
        if (cachedColorsPreset === preset && cachedColorStage == stage) return
        val base = stage.toInt()
        val fraction = stage - base
        val start = colorsForCycle(preset, base)
        val end = colorsForCycle(preset, base + 1)
        for (i in 0 until 16) {
            colorsBuffer[i] = start[i] + (end[i] - start[i]) * fraction
        }
        shader.setFloatUniform("uColors", colorsBuffer)
        cachedColorsPreset = preset
        cachedColorStage = stage
    }

    fun updateBound(
        height: Float,
        totalHeight: Float,
        totalWidth: Float,
    ) {
        if (cachedHeight == height && cachedWidth == totalWidth) return
        val heightRatio = height / totalHeight
        if (totalWidth <= totalHeight) {
            bound[0] = 0f
            bound[1] = 1f - heightRatio
            bound[2] = 1f
            bound[3] = heightRatio
        } else {
            val aspect = totalWidth / totalHeight
            bound[0] = 0f
            bound[1] = 1f - heightRatio / 2f - aspect / 2f
            bound[2] = 1f
            bound[3] = aspect
        }
        shader.setFloatUniform("uBound", bound)
        cachedHeight = height
        cachedWidth = totalWidth
    }

    fun updatePreset(preset: FlowLightConfig) {
        if (presetApplied === preset) return
        shader.setFloatUniform("uPoints", preset.points)
        shader.setFloatUniform("uLightOffset", preset.lightOffset)
        shader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        presetApplied = preset
    }

    private fun colorsForCycle(
        preset: FlowLightConfig,
        index: Int,
    ): FloatArray = when (index.mod(4)) {
        1 -> preset.colors1
        3 -> preset.colors3
        else -> preset.colors2
    }
}

private fun Modifier.flowLightDraw(
    painter: FlowLightPainter,
    preset: FlowLightConfig,
    surface: Color,
    isPlaying: Boolean,
    colorStage: () -> Float,
): Modifier = this then FlowLightElement(painter, preset, surface, isPlaying, colorStage)

private data class FlowLightElement(
    val painter: FlowLightPainter,
    val preset: FlowLightConfig,
    val surface: Color,
    val isPlaying: Boolean,
    val colorStage: () -> Float,
) : ModifierNodeElement<FlowLightNode>() {
    override fun create(): FlowLightNode = FlowLightNode(painter, preset, surface, isPlaying, colorStage)

    override fun update(node: FlowLightNode) {
        node.update(painter, preset, surface, isPlaying, colorStage)
    }
}

private class FlowLightNode(
    private var painter: FlowLightPainter,
    private var preset: FlowLightConfig,
    private var surface: Color,
    private var isPlaying: Boolean,
    private var colorStage: () -> Float,
) : Modifier.Node(),
    DrawModifierNode {
    private var job: Job? = null
    private var animTime = 0f
    private var startOffset = 0f

    override fun onAttach() {
        if (isPlaying) start()
    }

    override fun onDetach() {
        job?.cancel()
        job = null
    }

    fun update(
        painter: FlowLightPainter,
        preset: FlowLightConfig,
        surface: Color,
        isPlaying: Boolean,
        colorStage: () -> Float,
    ) {
        this.painter = painter
        this.preset = preset
        this.surface = surface
        this.colorStage = colorStage
        if (this.isPlaying != isPlaying) {
            this.isPlaying = isPlaying
            if (isPlaying) {
                start()
            } else {
                job?.cancel()
                job = null
            }
        }
        invalidateDraw()
    }

    private fun start() {
        job?.cancel()
        startOffset = animTime
        job = coroutineScope.launch {
            val minDeltaNanos = 1_000_000_000L / 60L
            val origin = withFrameNanos { it }
            var lastEmit = origin
            while (isActive) {
                val now = withFrameNanos { it }
                if (now - lastEmit < minDeltaNanos) continue
                lastEmit = now
                animTime = startOffset + (now - origin) / 1_000_000_000f
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surface)
        painter.updateResolution(size.width, size.height)
        painter.updateBound(size.height * 0.8f, size.height, size.width)
        painter.updatePreset(preset)
        painter.updateColors(preset, colorStage())
        painter.updateAnimTime(animTime)
        painter.updatePointsAnim(animTime, preset)
        drawRect(painter.brush)
        drawContent()
    }
}

private const val OS3_BG_FRAG = """
uniform vec2 uResolution;
uniform float uAnimTime;
uniform vec4 uBound;
uniform float uTranslateY;
uniform vec3 uPoints[4];
uniform vec2 uPointsAnim[4];
uniform vec4 uColors[4];
uniform float uAlphaMulti;
uniform float uNoiseScale;
uniform float uPointRadiusMulti;
uniform float uSaturateOffset;
uniform float uLightOffset;

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.13);
    p3 += dot(p3, p3.yzx + 3.333);
    return fract((p3.x + p3.y) * p3.z);
}

float perlin(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float gradientNoise(in vec2 uv) {
    return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
}

vec4 main(vec2 fragCoord) {
    vec2 vUv = fragCoord / uResolution;
    vUv.y = 1.0 - vUv.y;
    vec2 uv = vUv;
    uv -= vec2(0., uTranslateY);
    uv.xy -= uBound.xy;
    uv.xy /= uBound.zw;

    vec4 color = vec4(0.0);
    float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));

    for (int i = 0; i < 4; i++) {
        vec4 pointColor = uColors[i];
        pointColor.rgb *= pointColor.a;
        vec2 point = uPointsAnim[i];
        float rad = uPoints[i].z * uPointRadiusMulti;
        float d = distance(uv, point);
        float pct = smoothstep(rad, 0., d);
        color.rgb = mix(color.rgb, pointColor.rgb, pct);
        color.a = mix(color.a, pointColor.a, pct);
    }

    float oppositeNoise = smoothstep(0., 1., noiseValue);
    color.rgb /= color.a;
    vec3 hsv = rgb2hsv(color.rgb);
    hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
    color.rgb = hsv2rgb(hsv);
    color.rgb += oppositeNoise * uLightOffset;
    color.a = clamp(color.a, 0., 1.);
    color.a *= uAlphaMulti;
    color += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);
    return vec4(color.rgb * color.a, color.a);
}
"""
