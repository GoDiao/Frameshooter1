package com.example.frameshooter1

import android.content.Context
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class CustomCameraManager(private val context: Context, private val textureView: TextureView) {
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var previewRequest: CaptureRequest
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    fun startCamera() {
        val cameraId = cameraManager.cameraIdList[0] // Assuming we're using the first camera
        try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createCaptureSession(false)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        Log.e(TAG, "Camera device error: $error")
                    }
                }, cameraHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to access camera", e)
        }
    }

    fun startRecording() {
        if (isRecording) return

        try {
            createMediaRecorder()
            createCaptureSession(true)
            mediaRecorder?.start()
            isRecording = true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        try {
            captureSession.stopRepeating()
            captureSession.abortCaptures()
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            createCaptureSession(false)
            isRecording = false
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to stop recording", e)
        }
    }

    private fun createMediaRecorder() {
        val videoFile = createVideoFile()

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setOutputFile(videoFile.absolutePath)
            prepare()
        }
    }

    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoFileName = "VIDEO_$timeStamp.mp4"
        return File(context.getExternalFilesDir(null), videoFileName)
    }

    private fun createCaptureSession(forRecording: Boolean) {
        val texture = textureView.surfaceTexture
        texture?.setDefaultBufferSize(1080, 1920) // Set your desired preview size

        val previewSurface = Surface(texture)
        val surfaces = mutableListOf<Surface>(previewSurface)

        if (forRecording) {
            mediaRecorder?.surface?.let { surfaces.add(it) }
        }

        val templateType = if (forRecording) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
        previewRequestBuilder = cameraDevice.createCaptureRequest(templateType)
        for (surface in surfaces) {
            previewRequestBuilder.addTarget(surface)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val sessionConfiguration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                surfaces.map { OutputConfiguration(it) },
                ContextCompat.getMainExecutor(context),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        updatePreview()
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Failed to configure camera session")
                    }
                }
            )

            cameraDevice.createCaptureSession(sessionConfiguration)
        } else {
            @Suppress("DEPRECATION")
            cameraDevice.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    updatePreview()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Failed to configure camera session")
                }
            }, cameraHandler)
        }
    }

    private fun updatePreview() {
        try {
            previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            previewRequest = previewRequestBuilder.build()
            captureSession.setRepeatingRequest(previewRequest, null, cameraHandler)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to update preview", e)
        }
    }

    companion object {
        private const val TAG = "CustomCameraManager"
    }
}
