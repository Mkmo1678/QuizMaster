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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object ScreenCaptureHelper {

    private var mediaProjection: MediaProjection? = null
    private var resultCode: Int = 0
    private var resultData: Intent? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 初始化截图权限（需要在Activity中调用）
     */
    fun requestCapturePermission(activity: Activity, requestCode: Int) {
        val manager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        activity.startActivityForResult(manager.createScreenCaptureIntent(), requestCode)
    }

    /**
     * 在Activity的onActivityResult中调用，保存截图权限并创建MediaProjection
     * 必须在Activity中创建MediaProjection，不能在Service中创建
     */
    fun onCaptureResult(context: Context, resultCode: Int, data: Intent?) {
        this.resultCode = resultCode
        this.resultData = data
        android.util.Log.d("ScreenCapture", "onCaptureResult: resultCode=$resultCode")

        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = manager.getMediaProjection(resultCode, data)
                android.util.Log.d("ScreenCapture", "MediaProjection created successfully in Activity")
            } catch (e: Exception) {
                android.util.Log.e("ScreenCapture", "Failed to create MediaProjection", e)
            }
        }
    }

    /**
     * 检查是否已有截图权限
     */
    fun hasPermission(): Boolean {
        val has = mediaProjection != null
        android.util.Log.d("ScreenCapture", "hasPermission: $has")
        return has
    }

    /**
     * 截取当前屏幕
     */
    suspend fun captureScreen(context: Context): Bitmap? {
        val projection = mediaProjection
        if (projection == null) {
            android.util.Log.e("ScreenCapture", "MediaProjection is null, need to grant permission first")
            return null
        }

        return withContext(Dispatchers.Main) {
            try {
                val metrics = DisplayMetrics().also {
                    (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                        .defaultDisplay.getRealMetrics(it)
                }

                val width = metrics.widthPixels
                val height = metrics.heightPixels
                val density = metrics.densityDpi

                android.util.Log.d("ScreenCapture", "Capturing: ${width}x$height")

                val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                val virtualDisplay = projection.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface, null, null
                )

                val bitmap = waitForBitmap(imageReader, width, height)

                virtualDisplay.release()
                imageReader.close()

                bitmap
            } catch (e: Exception) {
                android.util.Log.e("ScreenCapture", "Capture failed", e)
                null
            }
        }
    }

    private suspend fun waitForBitmap(reader: ImageReader, width: Int, height: Int): Bitmap? {
        return withTimeoutOrNull(3000) {
            val deferred = CompletableDeferred<Bitmap?>()

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

                    val cropped = if (rowPadding > 0) {
                        Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    } else {
                        bitmap
                    }

                    mainHandler.post { deferred.complete(cropped) }
                } catch (e: Exception) {
                    android.util.Log.e("ScreenCapture", "Failed to process image", e)
                    mainHandler.post { deferred.complete(null) }
                }
            }, mainHandler)

            deferred.await()
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        mediaProjection?.stop()
        mediaProjection = null
    }
}
