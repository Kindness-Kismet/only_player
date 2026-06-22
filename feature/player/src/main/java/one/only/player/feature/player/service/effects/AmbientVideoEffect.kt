package one.only.player.feature.player.service.effects

import android.content.Context
import android.opengl.GLES20
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
internal class AmbientVideoEffect(
    private val targetAspectRatio: Float,
) : GlEffect {

    override fun toGlShaderProgram(
        context: Context,
        useHdr: Boolean,
    ): GlShaderProgram = AmbientShaderProgram(
        useHdr = useHdr,
        targetAspectRatio = targetAspectRatio,
    )

    override fun isNoOp(
        inputWidth: Int,
        inputHeight: Int,
    ): Boolean = false

    private class AmbientShaderProgram(
        useHdr: Boolean,
        private val targetAspectRatio: Float,
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
            val inputAspectRatio = inputWidth.toFloat() / inputHeight.toFloat()
            val outputAspectRatio = normalizedTargetAspectRatio(inputAspectRatio)
            val centerRect = centerRect(
                inputAspectRatio = inputAspectRatio,
                outputAspectRatio = outputAspectRatio,
            )
            glProgram.setFloatsUniform("uCenterMin", floatArrayOf(centerRect.left, centerRect.top))
            glProgram.setFloatsUniform("uCenterSize", floatArrayOf(centerRect.width, centerRect.height))
            glProgram.setFloatsUniform(
                "uBlurStep",
                floatArrayOf(
                    BLUR_STEP_PIXELS / inputWidth,
                    BLUR_STEP_PIXELS / inputHeight,
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

        private fun normalizedTargetAspectRatio(inputAspectRatio: Float): Float = targetAspectRatio
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(
                inputAspectRatio / MAX_CANVAS_EXPANSION,
                inputAspectRatio * MAX_CANVAS_EXPANSION,
            )
            ?: inputAspectRatio

        private fun createGlProgram(): GlProgram = try {
            GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        } catch (exception: GlUtil.GlException) {
            throw VideoFrameProcessingException(exception)
        }
    }

    private data class CenterRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
    )

    private companion object {
        private const val POSITION_COMPONENT_COUNT = 4
        private const val VERTEX_COUNT = 4
        private const val BLUR_STEP_PIXELS = 26f
        private const val MAX_CANVAS_EXPANSION = 2.4f

        private fun centerRect(
            inputAspectRatio: Float,
            outputAspectRatio: Float,
        ): CenterRect {
            if (outputAspectRatio >= inputAspectRatio) {
                val width = inputAspectRatio / outputAspectRatio
                return CenterRect(
                    left = (1f - width) / 2f,
                    top = 0f,
                    width = width,
                    height = 1f,
                )
            }

            val height = outputAspectRatio / inputAspectRatio
            return CenterRect(
                left = 0f,
                top = (1f - height) / 2f,
                width = 1f,
                height = height,
            )
        }

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
            uniform vec2 uCenterMin;
            uniform vec2 uCenterSize;
            uniform vec2 uBlurStep;
            varying vec2 vTexSamplingCoord;

            vec3 sampleSource(vec2 uv) {
              return texture2D(uTexSampler, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
            }

            vec2 sourceUvFromOutputUv(vec2 outputUv) {
              return (outputUv - uCenterMin) / uCenterSize;
            }

            vec3 ambientBlur(vec2 outputUv) {
              vec2 sourceUv = sourceUvFromOutputUv(outputUv);
              vec2 edgeUv = clamp(sourceUv, vec2(0.0), vec2(1.0));
              vec2 step1 = uBlurStep;
              vec2 step2 = uBlurStep * 2.0;
              vec2 edgeVector = edgeUv - sourceUv;
              float edgeDistance = length(edgeVector);
              vec2 inward = vec2(0.0, 0.0);
              if (edgeDistance >= 0.0001) {
                inward = edgeVector / edgeDistance;
              }

              vec3 color = sampleSource(edgeUv) * 0.20;
              color += sampleSource(edgeUv + inward * step1) * 0.12;
              color += sampleSource(edgeUv + inward * step2) * 0.10;
              color += sampleSource(edgeUv + vec2(step1.x, 0.0)) * 0.07;
              color += sampleSource(edgeUv - vec2(step1.x, 0.0)) * 0.07;
              color += sampleSource(edgeUv + vec2(0.0, step1.y)) * 0.07;
              color += sampleSource(edgeUv - vec2(0.0, step1.y)) * 0.07;
              color += sampleSource(edgeUv + vec2(step2.x, step1.y)) * 0.05;
              color += sampleSource(edgeUv + vec2(step2.x, -step1.y)) * 0.05;
              color += sampleSource(edgeUv + vec2(-step2.x, step1.y)) * 0.05;
              color += sampleSource(edgeUv + vec2(-step2.x, -step1.y)) * 0.05;
              color += sampleSource(edgeUv + vec2(step1.x, step2.y)) * 0.05;
              color += sampleSource(edgeUv + vec2(-step1.x, step2.y)) * 0.05;
              return color;
            }

            float outsideDistance(vec2 outputUv) {
              vec2 centerMax = uCenterMin + uCenterSize;
              vec2 nearest = clamp(outputUv, uCenterMin, centerMax);
              vec2 ambientSize = max(vec2(1.0) - uCenterSize, vec2(0.001));
              vec2 normalized = abs(outputUv - nearest) / ambientSize;
              return clamp(length(normalized) * 1.35, 0.0, 1.0);
            }

            void main() {
              vec2 centerMax = uCenterMin + uCenterSize;
              vec2 outputUv = vTexSamplingCoord;
              bool isCenter = outputUv.x >= uCenterMin.x &&
                outputUv.x <= centerMax.x &&
                outputUv.y >= uCenterMin.y &&
                outputUv.y <= centerMax.y;
              vec2 sourceUv = sourceUvFromOutputUv(outputUv);

              if (isCenter) {
                gl_FragColor = texture2D(uTexSampler, sourceUv);
                return;
              }

              float distance = outsideDistance(outputUv);
              vec3 color = ambientBlur(outputUv);
              float luma = dot(color, vec3(0.299, 0.587, 0.114));
              color = mix(color, vec3(luma), 0.12);
              color = pow(clamp(color * 1.08, 0.0, 1.0), vec3(0.92));
              color = mix(color, color * 0.42, smoothstep(0.0, 1.0, distance));
              gl_FragColor = vec4(color, 1.0);
            }
        """
    }
}
