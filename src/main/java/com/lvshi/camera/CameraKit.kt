package com.lvshi.camera

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import com.lvshi.camera.utils.LogUtil
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CameraKit(@NonNull val context: Context, @NonNull private val surfaceTexture: SurfaceTexture) {

    val TAG = "CameraKit"
    private val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
    private val SENSOR_ORIENTATION_INVERSE_DEGREES = 270

    private val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 90)
        append(Surface.ROTATION_90, 0)
        append(Surface.ROTATION_180, 270)
        append(Surface.ROTATION_270, 180)
    }
    private val INVERSE_ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 270)
        append(Surface.ROTATION_90, 180)
        append(Surface.ROTATION_180, 90)
        append(Surface.ROTATION_270, 0)
    }

    var videoPath: String? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    /**
     * The [android.util.Size] of video recording.
     */
    private lateinit var videoSize: Size

    /**
     * Orientation of the camera sensor
     */
    private var sensorOrientation = 0

    private var mediaRecorder: MediaRecorder? = null

    private var cameraDevice: CameraDevice? = null

    private var captureSession: CameraCaptureSession? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var backgroundHandler: Handler? = null

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var backgroundThread: HandlerThread? = null

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    private lateinit var currentCameraId: String

    private lateinit var cameraManager: CameraManager

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its status.
     */

    val mainHnadler = Handler(Looper.getMainLooper())

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val cameraOpenCloseLock = Semaphore(1)

    private val isRecordingVideo = AtomicBoolean(false)

    init {
        mediaRecorder = MediaRecorder()
        startBackgroundThread()
        if (checkSdkMoreThan21()) {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        }
    }

    fun isRecordingVideo(): Boolean = isRecordingVideo.get()

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = Handler(backgroundThread?.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            backgroundThread?.quitSafely()
        } else {
            backgroundThread?.quit()
        }
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private val stateCallback = @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            this@CameraKit.cameraDevice = cameraDevice
            LogUtil.d(TAG, "onOpened")
            startPreview()
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onDisconnected(cameraDevice: CameraDevice) {
            cameraOpenCloseLock.release()
            LogUtil.d(TAG, "onDisconnected")
            cameraDevice.close()
            this@CameraKit.cameraDevice = null
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onError(cameraDevice: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            LogUtil.d(TAG, "onError:${error}")
            cameraDevice.close()
            this@CameraKit.cameraDevice = null
        }

    }

    private fun checkSdkMoreThan21(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    private fun startPreview() {
        if (checkSdkMoreThan21()) {
            if (cameraDevice == null) return
            try {
                //closePreviewSession()
                val texture = surfaceTexture
                texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                val previewSurface = Surface(texture)
                previewRequestBuilder.addTarget(previewSurface)

                cameraDevice?.createCaptureSession(listOf(previewSurface),
                        object : CameraCaptureSession.StateCallback() {

                            override fun onConfigured(session: CameraCaptureSession) {
                                captureSession = session
                                updatePreview()
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                            }
                        }, backgroundHandler)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }

    /**
     * Update the camera preview. [startPreview] needs to be called in advance.
     */
    private fun updatePreview() {
        if (checkSdkMoreThan21()) {
            if (cameraDevice == null) return
            try {
                setUpCaptureRequestBuilder(previewRequestBuilder)
                captureSession?.setRepeatingRequest(previewRequestBuilder.build(),
                        null, backgroundHandler)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setUpCaptureRequestBuilder(builder: CaptureRequest.Builder?) {
        builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
    }

    private fun closePreviewSession() {
        if (checkSdkMoreThan21()) {
            captureSession?.apply {
                stopRepeating()
                abortCaptures()
                close()
            }
            captureSession = null
        }
    }

    @SuppressLint("MissingPermission")
    fun switchCamera(width: Int, height: Int) {
        releaseCamera()
        if (checkSdkMoreThan21()) {
            val lens = if (currentCameraId != CameraCharacteristics.LENS_FACING_FRONT.toString()) CameraCharacteristics.LENS_FACING_FRONT.toString() else CameraCharacteristics.LENS_FACING_BACK.toString()
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            var cameraId = cameraManager.cameraIdList.filter { it == lens }[0]
            currentCameraId = cameraId;
            // Choose the sizes for camera preview and video recording
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: throw RuntimeException("Cannot get available preview/video sizes")
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                    width, height, videoSize)
            cameraManager.openCamera(cameraId, stateCallback, mainHnadler)
        }
    }

    fun isFrontCamera() = (currentCameraId == CameraCharacteristics.LENS_FACING_FRONT.toString())

    /**
     * 相机预览
     */
    @SuppressLint("MissingPermission")
    fun startCameraPreview(width: Int, height: Int) {
        LogUtil.d(TAG, "startCameraPreview")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            val cameraId = manager.cameraIdList.filter { it == CameraCharacteristics.LENS_FACING_FRONT.toString() }[0]
            currentCameraId = cameraId;
            // Choose the sizes for camera preview and video recording
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: throw RuntimeException("Cannot get available preview/video sizes")
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture::class.java),
                    width, height, videoSize)
            manager.openCamera(cameraId, stateCallback, mainHnadler)
        }
    }


    @TargetApi(21)
    private fun chooseVideoSize(choices: Array<Size>) = choices.firstOrNull {
        it.width == it.height * 4 / 3 && it.width <= 1080
    } ?: choices[choices.size - 1]

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun chooseOptimalSize(sizes: Array<Size>, viewWidth: Int, viewHeight: Int, pictureSize: Size): Size {
        val totalRotation = getRotation()
        val swapRotation = totalRotation == 90 || totalRotation == 270
        val width = if (swapRotation) viewHeight else viewWidth
        val height = if (swapRotation) viewWidth else viewHeight
        return getSuitableSize(sizes, width, height, pictureSize)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getSuitableSize(sizes: Array<Size>, width: Int, height: Int, pictureSize: Size): Size {
        var minDelta = Int.MAX_VALUE // 最小的差值，初始值应该设置大点保证之后的计算中会被重置
        var index = 0 // 最小的差值对应的索引坐标
        val aspectRatio = pictureSize.height * 1.0f / pictureSize.width
        LogUtil.d(TAG, "getSuitableSize. aspectRatio: $aspectRatio")
        for (i in sizes.indices) {
            val size = sizes[i]
            // 先判断比例是否相等
            if (size.width * aspectRatio == size.height.toFloat()) {
                val delta = Math.abs(width - size.width)
                if (delta == 0) {
                    return size
                }
                if (minDelta > delta) {
                    minDelta = delta
                    index = i
                }
            }
        }
        return sizes[index]
    }

    private fun getRotation(): Int {
        var displayRotation: Int = (context as Activity).windowManager.defaultDisplay.rotation
        when (displayRotation) {
            Surface.ROTATION_0 -> displayRotation = 90
            Surface.ROTATION_90 -> displayRotation = 0
            Surface.ROTATION_180 -> displayRotation = 270
            Surface.ROTATION_270 -> displayRotation = 180
        }
        return (displayRotation + sensorOrientation + 270) % 360
    }

    /**
     * 开始视频录制
     */
    fun startRecordVideo(@NonNull videoPath: String) {
        this.videoPath = videoPath
        LogUtil.d(TAG, "vidoePath:${videoPath}")
        if (checkSdkMoreThan21()) {
            if (cameraDevice == null) return
            try {
                closePreviewSession()
                setUpMediaRecorder()
                val texture = surfaceTexture.apply {
                    setDefaultBufferSize(previewSize.width, previewSize.height)
                }

                // Set up Surface for camera preview and MediaRecorder
                val previewSurface = Surface(texture)
                val recorderSurface = mediaRecorder!!.surface
                val surfaces = ArrayList<Surface>().apply {
                    add(previewSurface)
                    add(recorderSurface)
                }
                previewRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(previewSurface)
                    addTarget(recorderSurface)
                }

                // Start a capture session
                // Once the session starts, we can update the UI and start recording
                cameraDevice?.createCaptureSession(surfaces,
                        object : CameraCaptureSession.StateCallback() {

                            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                                captureSession = cameraCaptureSession
                                updatePreview()
                                mainHnadler.post {
                                    isRecordingVideo.set(true)
                                    mediaRecorder?.start()
                                }
                            }

                            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                                LogUtil.d(TAG, "Camera configure failed")
                            }
                        }, backgroundHandler)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setUpMediaRecorder() {
        val rotation = (context as Activity).windowManager.defaultDisplay.rotation
        when (sensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                mediaRecorder?.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            SENSOR_ORIENTATION_INVERSE_DEGREES ->
                mediaRecorder?.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }
        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(videoPath)
            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(30)
            setVideoSize(videoSize.width, videoSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }
    }

    /**
     * @param release true:停止录制并释放相机资源
     */
    fun stopRecordVideo(release: Boolean = false) {
        isRecordingVideo.set(false)
        mediaRecorder?.apply {
            stop()
            reset()
        }
        videoPath = null
        if (release) {
            releaseCamera()
        }
    }

    /**
     * 释放所有资源
     */
    fun release() {
        try {
            cameraOpenCloseLock.acquire()
            stopBackgroundThread()
            releaseCamera()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * 释放相机资源
     */
    private fun releaseCamera() {
        if (checkSdkMoreThan21()) {
            closePreviewSession()
            cameraDevice?.close()
            cameraDevice = null
        }
    }
}