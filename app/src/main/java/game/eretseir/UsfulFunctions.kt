package game.eretseir

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

fun spToPx(sp : Float, context: Context) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)

fun showAlert(layoutInflater: LayoutInflater,
              root : ViewGroup,
              withAnimation : Boolean = true,
              description : String,
              buttonText : String,
              action : (View) -> Unit) {

    root.post {
        val alertScreen = layoutInflater.inflate(R.layout.alert, root, false)
        val alertWindow = alertScreen.findViewById<View>(R.id.alertWindow)
        alertWindow.findViewById<TextView>(R.id.textView).apply { text = description }
        alertWindow.findViewById<Button>(R.id.button).apply { text = buttonText; setOnClickListener(action) }
        root.addView(alertScreen)
        if (withAnimation) {
            val popUpAnim = AnimationUtils.loadAnimation(root.context, R.anim.view_popup)
            alertScreen.startAnimation(AlphaAnimation(0F, 1F).apply { duration = 500 })
            alertWindow.startAnimation(popUpAnim)
        }
    }
}

fun ViewGroup.removeWithAnimation(child : View) {
    post {
        val anim = AnimationUtils.loadAnimation(context, R.anim.view_remove)
        child.startAnimation(anim)
        removeView(child)
    }
}

fun ViewGroup.addWithAnimation(child: View) {
    post {
        val anim = AnimationUtils.loadAnimation(context, R.anim.view_popup)
        child.startAnimation(anim)
        addView(child)
    }
}