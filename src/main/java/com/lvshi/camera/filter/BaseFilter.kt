package com.lvshi.camera.filter

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.lvshi.camera.utils.OpenGLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

open class BaseFilter(var vertexShader: String? = null, var fragmentShader: String? = null) {

    internal var mWidth: Int = 0
    internal var mHeight: Int = 0
    internal var mTransforMatrix: FloatArray = FloatArray(0)
    private val DEFAULT_VERTEX_SHADER = "" +
            "attribute vec4 vPosition;" +
            "attribute vec2 inputTextureCoordinate;" +
            "varying vec2 textureCoordinate;" +
            "void main()" +
            "{" +
            "gl_Position = vPosition;" +
            "textureCoordinate = inputTextureCoordinate;" +
            "}"
    private val DEFAULT_FRAGMENT_SHADER = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 textureCoordinate;
        uniform samplerExternalOES inputImageTexture;
        void main() {  gl_FragColor = texture2D( inputImageTexture, textureCoordinate );
        }
        """.trimIndent()
    internal val mVertexBuffer: FloatBuffer
    internal val mBackTextureBuffer: FloatBuffer
    internal val mFrontTextureBuffer: FloatBuffer
    internal val mDrawListBuffer: ByteBuffer
    internal val mProgram: Int
    internal val mPositionHandle: Int
    internal val mTextureHandle: Int
    internal val VERTEX_SIZE = 2
    internal val VERTEX_STRIDE = VERTEX_SIZE * 4

    init {
        // init float buffer for vertex coordinates
        mVertexBuffer = ByteBuffer.allocateDirect(VERTEXES.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        mVertexBuffer.put(VERTEXES).position(0)

        // init float buffer for texture coordinates
        mBackTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_BACK.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        mBackTextureBuffer.put(TEXTURE_BACK).position(0)
        mFrontTextureBuffer = ByteBuffer.allocateDirect(TEXTURE_FRONT.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        mFrontTextureBuffer.put(TEXTURE_FRONT).position(0)

        // init byte buffer for draw list
        mDrawListBuffer = ByteBuffer.allocateDirect(VERTEX_ORDER.size).order(ByteOrder.nativeOrder())
        mDrawListBuffer.put(VERTEX_ORDER).position(0)
        vertexShader = vertexShader ?: DEFAULT_VERTEX_SHADER
        fragmentShader = fragmentShader ?: DEFAULT_FRAGMENT_SHADER
        mProgram = OpenGLUtils.createProgram(vertexShader, fragmentShader)
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        mTextureHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate")
    }

    open fun draw(texture: Int, isFrontCamera: Boolean) {
        GLES20.glUseProgram(mProgram) // 指定使用的program
        predraw()
        GLES20.glEnable(GLES20.GL_CULL_FACE) // 启动剔除
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture) // 绑定纹理
        GLES20.glEnableVertexAttribArray(mPositionHandle)
        GLES20.glVertexAttribPointer(mPositionHandle, VERTEX_SIZE, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mVertexBuffer)
        GLES20.glEnableVertexAttribArray(mTextureHandle)
        if (isFrontCamera) {
            GLES20.glVertexAttribPointer(mTextureHandle, VERTEX_SIZE, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mFrontTextureBuffer)
        } else {
            GLES20.glVertexAttribPointer(mTextureHandle, VERTEX_SIZE, GLES20.GL_FLOAT, false, VERTEX_STRIDE, mBackTextureBuffer)
        }
        // 真正绘制的操作
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_FAN, VERTEX_ORDER.size, GLES20.GL_UNSIGNED_BYTE, mDrawListBuffer)
        GLES20.glDisableVertexAttribArray(mPositionHandle)
        GLES20.glDisableVertexAttribArray(mTextureHandle)
    }

    open fun configTransfirmMartix(mtx: FloatArray) {
        mTransforMatrix = mtx
    }

    open fun predraw() {

    }

    open fun configSize(width: Int, height: Int) {
        mWidth = width
        mHeight = height
    }

    companion object {
        internal val VERTEXES = floatArrayOf(
                -1.0f, 1.0f,
                -1.0f, -1.0f,
                1.0f, -1.0f,
                1.0f, 1.0f)

        // 后置摄像头使用的纹理坐标
        internal val TEXTURE_BACK = floatArrayOf(
                0.0f, 1.0f,
                1.0f, 1.0f,
                1.0f, 0.0f,
                0.0f, 0.0f)

        // 前置摄像头使用的纹理坐标
        internal val TEXTURE_FRONT = floatArrayOf(
                1.0f, 1.0f,
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f)
        internal val VERTEX_ORDER = byteArrayOf(0, 1, 2, 3) // order to draw vertices
    }

}