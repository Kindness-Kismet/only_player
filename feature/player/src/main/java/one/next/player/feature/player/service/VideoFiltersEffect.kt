package one.next.player.feature.player.service

import android.content.Context
import android.opengl.GLES20
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

@OptIn(UnstableApi::class)
internal class VideoFiltersEffect(
    private val transition: VideoFilterTransition,
    private val transitionDurationMs: Long,
) : GlEffect {

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean,
    ): GlShaderProgram = VideoFiltersShaderProgram(
        useHdr = useHdr,
        transition = transition,
        transitionDurationMs = transitionDurationMs,
    )

    override fun isNoOp(
        inputWidth: Int,
        inputHeight: Int,
    ): Boolean = false

    private class VideoFiltersShaderProgram(
        useHdr: Boolean,
        private val transition: VideoFilterTransition,
        private val transitionDurationMs: Long,
    ) : BaseGlShaderProgram(useHdr, 1) {

        private val glProgram = createGlProgram()

        init {
            val identityMatrix = GlUtil.create4x4IdentityMatrix()
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                POSITION_COMPONENT_COUNT,
            )
            glProgram.setFloatsUniform("uTransformationMatrix", identityMatrix)
            glProgram.setFloatsUniform("uTexTransformationMatrix", identityMatrix)
        }

        override fun configure(
            inputWidth: Int,
            inputHeight: Int,
        ): Size {
            glProgram.setFloatsUniform(
                "uTexelSize",
                floatArrayOf(
                    1f / inputWidth,
                    1f / inputHeight,
                ),
            )
            return Size(inputWidth, inputHeight)
        }

        override fun drawFrame(
            inputTexId: Int,
            presentationTimeUs: Long,
        ) {
            try {
                glProgram.use()
                setFilterUniforms(transitionProgress())
                glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                glProgram.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
                GlUtil.checkGlError()
            } catch (exception: GlUtil.GlException) {
                throw VideoFrameProcessingException(exception, presentationTimeUs)
            }
        }

        override fun release() {
            super.release()
            try {
                glProgram.delete()
            } catch (exception: GlUtil.GlException) {
                throw VideoFrameProcessingException(exception)
            }
        }

        private fun transitionProgress(): Float = transition.progress(
            currentMs = SystemClock.elapsedRealtime(),
            durationMs = transitionDurationMs,
        )

        private fun setFilterUniforms(progress: Float) {
            val startFilters = transition.startFilters
            val targetFilters = transition.targetFilters
            glProgram.setFloatUniform("uBrightness", startFilters.brightness.interpolate(targetFilters.brightness, progress))
            glProgram.setFloatUniform("uContrast", startFilters.contrast.interpolate(targetFilters.contrast, progress))
            glProgram.setFloatUniform("uSaturation", startFilters.saturation.interpolate(targetFilters.saturation, progress))
            glProgram.setFloatUniform("uHue", startFilters.hue.interpolate(targetFilters.hue, progress))
            glProgram.setFloatUniform("uGamma", startFilters.gamma.interpolate(targetFilters.gamma, progress))
            glProgram.setFloatUniform("uSharpness", startFilters.sharpening.interpolate(targetFilters.sharpening, progress) * SHARPNESS_SCALE)
        }

        private fun createGlProgram(): GlProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (exception: GlUtil.GlException) {
            throw VideoFrameProcessingException(exception)
        }
    }

    private companion object {
        private const val POSITION_COMPONENT_COUNT = 4
        private const val VERTEX_COUNT = 4
        private const val SHARPNESS_SCALE = 1.25f

        private const val VERTEX_SHADER = """
            #version 100
            attribute vec4 aFramePosition;
            uniform mat4 uTransformationMatrix;
            uniform mat4 uTexTransformationMatrix;
            varying vec2 vTexSamplingCoord;

            void main() {
              gl_Position = uTransformationMatrix * aFramePosition;
              vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5,
                                          aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
              vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #version 100
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uBrightness;
            uniform float uContrast;
            uniform float uSaturation;
            uniform float uHue;
            uniform float uGamma;
            uniform float uSharpness;
            varying vec2 vTexSamplingCoord;

            vec3 rotateHue(vec3 color, float hue) {
              float angle = hue * 0.01745329252;
              float sine = sin(angle);
              float cosine = cos(angle);
              vec3 yiq = vec3(
                dot(color, vec3(0.299, 0.587, 0.114)),
                dot(color, vec3(0.596, -0.274, -0.322)),
                dot(color, vec3(0.211, -0.523, 0.312))
              );
              vec2 chroma = vec2(
                yiq.y * cosine - yiq.z * sine,
                yiq.y * sine + yiq.z * cosine
              );
              yiq = vec3(yiq.x, chroma.x, chroma.y);
              return vec3(
                dot(yiq, vec3(1.0, 0.956, 0.621)),
                dot(yiq, vec3(1.0, -0.272, -0.647)),
                dot(yiq, vec3(1.0, -1.106, 1.703))
              );
            }

            vec3 applyColorFilters(vec3 color) {
              float brightness = uBrightness;
              float contrast = 1.0 + uContrast;
              float saturation = 1.0 + uSaturation / 100.0;
              float hue = uHue;
              float gamma = max(uGamma, 0.1);
              float luma;

              color = clamp(color + vec3(brightness), 0.0, 1.0);
              color = clamp((color - vec3(0.5)) * contrast + vec3(0.5), 0.0, 1.0);
              color = clamp(rotateHue(color, hue), 0.0, 1.0);
              luma = dot(color, vec3(0.299, 0.587, 0.114));
              color = clamp(mix(vec3(luma), color, saturation), 0.0, 1.0);
              return clamp(pow(color, vec3(1.0 / gamma)), 0.0, 1.0);
            }

            void main() {
              vec4 center = texture2D(uTexSampler, vTexSamplingCoord);
              vec3 centerColor = applyColorFilters(center.rgb);

              if (uSharpness > 0.0) {
                vec3 north = applyColorFilters(texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, -uTexelSize.y)).rgb);
                vec3 south = applyColorFilters(texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexelSize.y)).rgb);
                vec3 west = applyColorFilters(texture2D(uTexSampler, vTexSamplingCoord + vec2(-uTexelSize.x, 0.0)).rgb);
                vec3 east = applyColorFilters(texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, 0.0)).rgb);
                vec3 blur = (north + south + west + east) * 0.25;
                centerColor = clamp(centerColor + (centerColor - blur) * uSharpness, 0.0, 1.0);
              }

              gl_FragColor = vec4(centerColor, center.a);
            }
        """
    }
}
