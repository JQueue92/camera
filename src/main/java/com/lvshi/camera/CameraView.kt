package com.lvshi.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.View
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import com.lvshi.camera.utils.LogUtil
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraView(context: Context, attributeSet: AttributeSet) :
        GLSurfaceView(context, attributeSet),
        GLSurfaceView.Renderer,
        SurfaceTexture.OnFrameAvailableListener {

    val TAG = "Camera"

    private var textureID = 0
    private lateinit var surfaceTexture: SurfaceTexture
    private lateinit var cameraKit: CameraKit
    private lateinit var mDirectDrawer: DirectDrawer

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /**创建显示的texture */
    private fun createTextureID(): Int {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0])
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE)
        return texture[0]
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (::surfaceTexture.isInitialized) {
            surfaceTexture.updateTexImage()
        }
        mDirectDrawer.draw(textureID, !isFrontCamera())
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        LogUtil.d(TAG, "onSurfaceChanged")
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        textureID = createTextureID()
        surfaceTexture = SurfaceTexture(textureID)
        surfaceTexture.setOnFrameAvailableListener(this)
        mDirectDrawer = DirectDrawer()
        // open camera
        cameraKit = CameraKit(context, surfaceTexture)
        if(ActivityCompat.checkSelfPermission(context,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            cameraKit.startCameraPreview(width, height)
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        requestRender()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        LogUtil.d(TAG, "onVisibilityChanged:${visibility}")
        when (visibility) {
            View.VISIBLE -> {
                if (::cameraKit.isInitialized) {
                    cameraKit.startCameraPreview(width, height)
                }
            }
            else -> {
                if (::cameraKit.isInitialized) {
                    cameraKit.release()
                }
            }
        }
    }

    fun startPreview(){
        if(ActivityCompat.checkSelfPermission(context,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            cameraKit.startCameraPreview(width, height)
        }
    }

    fun startRecordVideo(@NonNull videoPath: String) {
        cameraKit?.startRecordVideo(videoPath)
    }

    fun isRecordingVideo(): Boolean = cameraKit?.isRecordingVideo()

    fun stopRecordVideo() {
        cameraKit?.stopRecordVideo()
    }

    fun switchCamera() {
        cameraKit?.switchCamera(width, height)
    }

    fun isFrontCamera(): Boolean = cameraKit?.isFrontCamera()

}