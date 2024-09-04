package com.example.frameshooter1

import android.content.Context
import android.media.Image
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

class ImageProcessor(private val context: Context) {

    private var lastCaptureTime: Long = 0

    fun processImage(image: Image) {
        if (System.currentTimeMillis() - lastCaptureTime >= 2000) {
            captureFrame(image)
            lastCaptureTime = System.currentTimeMillis()
        }
    }

    private fun captureFrame(image: Image) {
        val buffer: ByteBuffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Frame_$timestamp.jpg"
        val file = File(context.getExternalFilesDir(null), filename)

        try {
            FileOutputStream(file).use { output ->
                output.write(bytes)
            }
            Log.d("ImageProcessor", "Saved frame: ${file.absolutePath}")
        } catch (e: IOException) {
            Log.e("ImageProcessor", "Error saving frame", e)
        }
    }
}
