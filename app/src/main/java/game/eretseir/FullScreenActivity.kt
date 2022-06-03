package game.eretseir

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*

open class FullScreenActivity : AppCompatActivity() {

    private lateinit var mGestureDetector : GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //hide keyboard on swipe down
        mGestureDetector = GestureDetectorCompat(this, FlingListener())
        //edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        //hide bars every time the keyboard closes
        val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            val displayRect = Rect().apply { window.decorView.getWindowVisibleDisplayFrame(this) }
            val keypadHeight = window.decorView.rootView.height - displayRect.bottom
            if (keypadHeight > 0)
                makeMeFullScreen()
        }
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        //close keyboard on swipe down
        mGestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    fun closeKeyboard() {
        this.currentFocus?.let { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun onStart() {
        makeMeFullScreen()
        super.onStart()
    }

    override fun onResume() {
        makeMeFullScreen()
        super.onResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus)
            makeMeFullScreen()
    }

    private fun makeMeFullScreen() {
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private inner class FlingListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
            val sensitivity = window.decorView.height / 5
            if (e1 == null || e2 == null) return true
            if (e2.y - e1.y > sensitivity && velocityY > 5)
                closeKeyboard()
            return true
        }
    }
}