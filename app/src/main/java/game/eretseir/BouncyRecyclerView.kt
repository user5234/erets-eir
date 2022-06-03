package game.eretseir

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

open class BouncyRecyclerView : RecyclerView {

    companion object {
        //helper function to run a function on all view holders
        private inline fun <reified T : RecyclerView.ViewHolder> RecyclerView.forEachVisibleHolder(action: (T) -> Unit) {
            for (i in 0 until childCount)
                action(getChildViewHolder(getChildAt(i)) as T)
        }
    }

    /**
     * The magnitude of translation distance while the list is over-scrolled
     */
    var overScrollTranslationMagnitude = 0.2f

    /**
     * The magnitude of translation distance when the list reaches the edge on fling
     */
    var flingTranslationMagnitude = 0.5f

    constructor(ctx: Context) : super(ctx)

    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int) : super(ctx, attrs, defStyleAttr)

    init {
        edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {

            override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {

                val edgeEffect = object : EdgeEffect(recyclerView.context) {

                    override fun onPull(deltaDistance: Float) {
                        super.onPull(deltaDistance)
                        handlePull(deltaDistance)
                    }

                    override fun onPull(deltaDistance: Float, displacement: Float) {
                        super.onPull(deltaDistance, displacement)
                        handlePull(deltaDistance)
                    }

                    private fun handlePull(deltaDistance: Float) {
                        // This is called on every touch event while the list is scrolled with a finger.
                        // We simply update the view properties without animation.
                        val sign =
                            if (direction == DIRECTION_BOTTOM) -1 else 1
                        val translationYDelta =
                            sign * recyclerView.width * deltaDistance * overScrollTranslationMagnitude
                        forEachVisibleHolder { holder: ViewHolder ->
                            holder.translationYAnim.cancel()
                            holder.itemView.translationY += translationYDelta
                        }
                    }

                    override fun onRelease() {
                        super.onRelease()
                        // The finger is lifted. This is when we should start the animations to bring
                        // the view property values back to their resting states.
                        forEachVisibleHolder { holder: ViewHolder ->
                            holder.translationYAnim.start()
                        }
                    }

                    override fun onAbsorb(velocity: Int) {
                        super.onAbsorb(velocity)
                        val sign = if (direction == DIRECTION_BOTTOM) -1 else 1
                        // The list has reached the edge on fling.
                        val translationVelocity = sign * velocity * flingTranslationMagnitude
                        forEachVisibleHolder { holder: ViewHolder ->
                            holder.translationYAnim
                                .setStartVelocity(translationVelocity)
                                .start()
                        }
                    }
                }

                edgeEffect.color = Color.argb(0, 0, 0, 0)

                return edgeEffect
            }
        }
    }

    open class ViewHolder(
        item : View,
        dampingRatio : Float = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY,
        stiffness : Float = SpringForce.STIFFNESS_LOW) : RecyclerView.ViewHolder(item) {

        /**
         * A [SpringAnimation] for this RecyclerView item. This animation is used to bring the item back
         * after the over-scroll effect.
         */
        val translationYAnim: SpringAnimation = SpringAnimation(itemView, SpringAnimation.TRANSLATION_Y)
            .setSpring(
                SpringForce()
                    .setFinalPosition(0f)
                    .setDampingRatio(dampingRatio)
                    .setStiffness(stiffness)
            )
    }
}