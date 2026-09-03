package com.quizmaster.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoClickService"
        var instance: AutoClickAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要自动监听事件，由悬浮窗主动调用
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * 点击指定坐标
     */
    fun clickAt(x: Float, y: Float, callback: (Boolean) -> Unit = {}) {
        try {
            val path = Path().apply {
                moveTo(x, y)
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    callback(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    callback(false)
                }
            }, null)
        } catch (e: Exception) {
            Log.e(TAG, "Click failed", e)
            callback(false)
        }
    }

    /**
     * 根据文字查找并点击
     */
    fun clickByText(text: String): Boolean {
        try {
            val rootNode = rootInActiveWindow ?: return false
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            if (nodes.isNotEmpty()) {
                val node = nodes[0]
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                clickAt(rect.exactCenterX(), rect.exactCenterY())
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Click by text failed", e)
        }
        return false
    }

    /**
     * 获取当前屏幕所有可点击节点的文字和位置
     */
    fun getClickableNodes(): List<NodeInfo> {
        val result = mutableListOf<NodeInfo>()
        try {
            val rootNode = rootInActiveWindow ?: return result
            traverseNodes(rootNode, result)
        } catch (e: Exception) {
            Log.e(TAG, "Get nodes failed", e)
        }
        return result
    }

    private fun traverseNodes(node: AccessibilityNodeInfo, result: MutableList<NodeInfo>) {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val isClickable = node.isClickable

        if ((text.isNotBlank() || contentDesc.isNotBlank()) && isClickable) {
            result.add(
                NodeInfo(
                    text = text.ifBlank { contentDesc },
                    x = rect.exactCenterX(),
                    y = rect.exactCenterY(),
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom
                )
            )
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { traverseNodes(it, result) }
        }
    }

    data class NodeInfo(
        val text: String,
        val x: Float,
        val y: Float,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )
}
