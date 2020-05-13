package com.lvshi.camera.filter

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.example.camera.R
import com.lvshi.camera.utils.OpenGLUtils
import java.nio.FloatBuffer

class BeautyFilter(context: Context) : BaseFilter(
        OpenGLUtils.readShaderFromRawResource(context, R.raw.default_vertex
        ), OpenGLUtils.readShaderFromRawResource(context, R.raw.default_fragment)) {

    private var mParamsLocation: Int = 0
    private var mTextureTransformMatrixLocation = 0
    private var mSingleStepOffsetLocation = 0

    private var beautyLevel: Float = 0.33.toFloat()

    init {
        mTextureTransformMatrixLocation = GLES20.glGetUniformLocation(mProgram, "textureTransform")
        mSingleStepOffsetLocation = GLES20.glGetUniformLocation(mProgram, "singleStepOffset")
        mParamsLocation = GLES20.glGetUniformLocation(mProgram, "params")
    }

    override fun predraw() {
        super.predraw()
        GLES20.glUniform1f(mParamsLocation, beautyLevel)
        GLES20.glUniform2fv(mSingleStepOffsetLocation, 1, FloatBuffer.wrap(FloatArray(2).apply {
            set(0, 2.toFloat() / mWidth)
            set(1, 2.toFloat() / mHeight)
        }))
    }

    fun setBeautyLevel(level: Int = 5) {
        beautyLevel = when (level) {
            0 -> {
                0.0f
            }
            1 -> {
                1.0f
            }
            2 -> {
                0.8f
            }
            3 -> {
                0.6f
            }
            4 -> {
                0.4f
            }
            5 -> {
                0.33f
            }
            else -> {
                0.33f
            }
        }
    }


}