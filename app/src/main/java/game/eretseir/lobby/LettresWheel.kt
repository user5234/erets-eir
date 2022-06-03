package game.eretseir.lobby

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.RotateDrawable
import android.util.AttributeSet
import android.view.animation.PathInterpolator
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.withRotation
import androidx.core.view.doOnLayout
import game.eretseir.R
import kotlin.random.Random


class LettresWheel : AppCompatImageView {

    var letters = mutableListOf("1", "2", "3", "4")
    //the angle in each arc of the wheel
    private var angle = 360F / letters.size
    //the starting angle for the first arc
    private var startAngle = - angle / 2

    //the enclosing rectangle of the wheel
    private lateinit var rect : RectF
    //the wheel rotate drawable
    private lateinit var wheel : RotateDrawable

    constructor(ctx: Context) : super(ctx)

    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs) { addLetters(attrs, 0) }

    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int) : super(ctx, attrs, defStyleAttr) { addLetters(attrs, defStyleAttr) }

    init {
        doOnLayout {
            rect = RectF(0.11F * width, 0.11F * height, 0.89F * width, 0.89F * height)
            setImageResource(R.drawable.letters_wheel_outer_ring)
            wheel = createWheelDrawable(listOf(Color.RED, Color.WHITE))
        }
    }

    private fun addLetters(attrs : AttributeSet, defStyleAttr : Int) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LettresWheel, defStyleAttr, 0)
        letters = ta.getString(R.styleable.LettresWheel_letters)?.split(",")?.toMutableList() ?: letters
        angle = 360F / letters.size
        startAngle = - angle / 2
        ta.recycle()
    }

    private fun createWheelDrawable(@ColorInt colorList : List<Int>) : RotateDrawable {
        val paintList = colorList.map { Paint().apply { color = it; isAntiAlias = true } }
        val textPaint = Paint().apply { color = Color.BLACK; textSize = width / 20F; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val fm = textPaint.fontMetrics
        val textHeight = fm.ascent + fm.descent + fm.leading
        val bitmap = Bitmap.createBitmap(rect.width().toInt(), rect.height().toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bitmapBounds = RectF(0F, 0F, bitmap.width.toFloat(), bitmap.height.toFloat())
        for (i in letters.indices) {
            canvas.drawArc(bitmapBounds, startAngle + i * angle, angle, true, paintList[i % paintList.size])
            canvas.withRotation(angle * i, bitmap.width / 2F, bitmap.height / 2F) {
                drawText(letters[i], bitmap.width * 0.92F, bitmap.height / 2F - textHeight / 2, textPaint)
            }
        }
        return RotateDrawable().apply {
            pivotX = 0.5F
            pivotY = 0.5F
            fromDegrees = 0F
            drawable = BitmapDrawable(resources, bitmap)
            bounds = Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        }
    }

    fun spin(duration : Long = 6000) : String {
        val randomNumber = Random(System.nanoTime()).nextInt(letters.size)
        wheel.toDegrees = 360 * 3 - randomNumber * angle
        ObjectAnimator.ofInt(wheel, "level", 0, 10000).apply {
            interpolator = PathInterpolator(0.335F, 0.070F, 0.045F, 1.080F)
            this.duration = duration
            addUpdateListener { postInvalidate() }
            start()
        }
        return letters[randomNumber]
    }

    override fun onDraw(canvas: Canvas?) {
        if (canvas == null)
            return
        wheel.draw(canvas)
        super.onDraw(canvas)
    }
}