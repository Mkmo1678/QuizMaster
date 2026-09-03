package com.quizmaster.helper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

object ScreenCaptureHelper {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var resultCode: Int = 0
    private var resultData: Intent? = null

    /**
     * 初始化截图权限（需要在Activity中调用）
     */
    fun requestCapturePermission(activity: Activity, requestCode: Int) {
        val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
    }

    /**
     * 在Activity的onActivityResult中调用，保存截图权限
     */
    fun onCaptureResult(resultCode: Int, data: Intent?) {
        this.resultCode = resultCode
        this.resultData = data
    }

    /**
     * 检查是否已有截图权限
     */
    fun hasPermission(): Boolean {
        return resultCode == Activity.RESULT_OK && resultData != null
    }

    /**
     * 截取当前屏幕
     */
    suspend fun captureScreen(context: Context): Bitmap? {
        if (!hasPermission()) return null

        return try {
            val projection = getMediaProjection(context) ?: return null
            val metrics = DisplayMetrics().also {
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay.getRealMetrics(it)
            }

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val display = projection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null
            )

            // 等待一帧
            val bitmap = waitForBitmap(reader, width, height)

            display.release()
            reader.close()

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getMediaProjection(context: Context): MediaProjection? {
        if (mediaProjection == null) {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(resultCode, resultData ?: return null)
        }
        return mediaProjection
    }

    private suspend fun waitForBitmap(reader: ImageReader, width: Int, height: Int): Bitmap? {
        return withTimeoutOrNull(2000) {
            val deferred = CompletableDeferred<Bitmap?>()
            val handler = Handler(Looper.getMainLooper())

            reader.setOnImageAvailableListener({ r ->
                try {
                    val image: Image = r.acquireLatestImage()
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    // 裁剪掉padding
                    val cropped = if (rowPadding > 0) {
                        Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    } else {
                        bitmap
                    }

                    handler.post { deferred.complete(cropped) }
                } catch (e: Exception) {
                    handler.post { deferred.complete(null) }
                }
            }, handler)

            deferred.await()
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }
}
