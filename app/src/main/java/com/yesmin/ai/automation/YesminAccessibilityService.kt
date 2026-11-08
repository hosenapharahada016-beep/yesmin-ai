package com.yesmin.ai.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional screen control. It does nothing until the user enables it in Android's
 * Accessibility settings, and it only acts when the app calls it from a user command.
 * It never reads or acts in the background on its own, and screen text is only read
 * when the user presses "Read screen".
 */
class YesminAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* intentionally unused */ }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Visible text on the current screen (excluding password fields). */
    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || sb.length > 6000) return
            if (n.isVisibleToUser && !n.isPassword) {
                val t = n.text?.toString()?.trim().orEmpty()
                val d = n.contentDescription?.toString()?.trim().orEmpty()
                if (t.isNotEmpty()) sb.appendLine(t) else if (d.isNotEmpty()) sb.appendLine(d)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return sb.toString().trim()
    }

    /** Clicks the first visible clickable node whose text/description contains [label]. */
    fun clickByText(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(label)
        for (n in nodes) {
            var cur: AccessibilityNodeInfo? = n
            while (cur != null) {
                if (cur.isClickable && cur.isVisibleToUser) {
                    return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                cur = cur.parent
            }
        }
        return false
    }

    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun scrollDown(): Boolean = swipe(0.5f, 0.75f, 0.5f, 0.25f)
    fun scrollUp(): Boolean = swipe(0.5f, 0.25f, 0.5f, 0.75f)

    /** Dispatches a swipe gesture. Returns true if the system accepted it for dispatch. */
    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val dm = resources.displayMetrics
        val p = Path().apply {
            moveTo(dm.widthPixels * x1, dm.heightPixels * y1)
            lineTo(dm.widthPixels * x2, dm.heightPixels * y2)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 350))
            .build()
        return dispatchGesture(g, null, null)
    }

    companion object {
        @Volatile var instance: YesminAccessibilityService? = null
        val isEnabled get() = instance != null
    }
}
