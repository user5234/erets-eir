package game.eretseir

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import androidx.core.view.children


class BetterRadioGroup : LinearLayout {

    var checkedRadioButton: RadioButton? = null

    private val buttons = mutableListOf<RadioButton>()

    constructor(ctx: Context) : super(ctx)

    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int) : super(ctx, attrs, defStyleAttr)

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        super.addView(child, index, params)
        if (child is ViewGroup)
            return findCheckableChildren(child)
        if (child is RadioButton)
            return addButton(child)
    }

    private fun addButton(button: RadioButton) {
        buttons.add(button)
        button.setOnClickListener {
            buttons.forEach {
                it.isChecked = (it == button)
                if (it.isChecked) checkedRadioButton = it
            }
        }
        button.setOnCheckedChangeListener { _, isChecked -> if (isChecked) checkedRadioButton = button }
    }

    private fun findCheckableChildren(root: ViewGroup) {
        root.children.forEach {
            if (it is ViewGroup)
                findCheckableChildren(it)
            else if (it is RadioButton)
                addButton(it)
        }
    }
}