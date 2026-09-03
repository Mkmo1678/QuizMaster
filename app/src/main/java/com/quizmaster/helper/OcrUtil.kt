package com.quizmaster.helper

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrUtil {

    private const val TAG = "OcrUtil"

    // 中文识别器
    private val chineseRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    /**
     * 识别图片中的文字
     */
    suspend fun recognizeText(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            chineseRecognizer.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text
                    continuation.resume(text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR recognition failed", e)
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * 识别图片中的文字，带位置信息
     */
    suspend fun recognizeTextWithBlocks(bitmap: Bitmap): List<TextBlock> {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            chineseRecognizer.process(image)
                .addOnSuccessListener { result ->
                    val blocks = mutableListOf<TextBlock>()
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            val boundingBox = line.boundingBox
                            if (boundingBox != null) {
                                blocks.add(
                                    TextBlock(
                                        text = line.text,
                                        left = boundingBox.left,
                                        top = boundingBox.top,
                                        right = boundingBox.right,
                                        bottom = boundingBox.bottom
                                    )
                                )
                            }
                        }
                    }
                    continuation.resume(blocks)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR recognition failed", e)
                    continuation.resumeWithException(e)
                }
        }
    }

    data class TextBlock(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int get() = (left + right) / 2
        val centerY: Int get() = (top + bottom) / 2
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }
}
