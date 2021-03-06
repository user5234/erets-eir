package game.eretseir.game.adapters


import android.graphics.Color
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import gal.libs.bouncyrecyclerview.BouncyRecyclerView
import game.eretseir.R

/**
 * @param submittedAnswers will be changes as the player submits new answers
 */
class GameSolutionsRecyclerAdapter(private val solutions : Collection<String>, private val submittedAnswers : MutableCollection<String>) : RecyclerView.Adapter<GameSolutionsRecyclerAdapter.ViewHolder>() {

    private val mData = mutableListOf(HolderData())
    private val recyclerViews = mutableListOf<RecyclerView>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.game_solutions_list_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bindData(mData[position])

    override fun getItemCount(): Int = mData.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerViews.add(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerViews.remove(recyclerView)
    }

    private fun String.parse() = replace("-", " ").replace("'", "")

    //the ViewHolder class
    inner class ViewHolder(private val item : View) : BouncyRecyclerView.ViewHolder(item) {

        internal fun bindData(data : HolderData) {
            item.findViewById<EditText>(R.id.editText).apply { removeTextChangedListener(data.watcher) }
            data.watcher = null
            item.findViewById<MaterialCardView>(R.id.cardView).strokeColor = if (data.isEditable) Color.RED else Color.GREEN
            item.findViewById<EditText>(R.id.editText).apply {
                isClickable = data.isEditable
                isFocusable = data.isEditable
                isFocusableInTouchMode = data.isEditable
                setText(data.text)
            }
            if (!data.isEditable)
                return
            item.findViewById<EditText>(R.id.editText).apply {
                data.watcher = doOnTextChanged { textCharSequence, _, _, _ ->
                    val text = textCharSequence.toString()
                    solutions.forEach { solution ->
                        if (solution.parse() == text.parse() && solution !in submittedAnswers) {
                            submittedAnswers.add(solution)
                            data.let { it.isEditable = false; it.text = text }
                            bindData(data)
                            mData.add(HolderData())
                            notifyItemInserted(itemCount)
                            recyclerViews.forEach { it.post { it.smoothScrollToPosition(itemCount - 1) } }
                            return@doOnTextChanged
                        }
                    }
                }
                requestFocus()
            }
        }
    }

    internal data class HolderData(var text : String = "", var isEditable: Boolean = true, var watcher: TextWatcher? = null)
}