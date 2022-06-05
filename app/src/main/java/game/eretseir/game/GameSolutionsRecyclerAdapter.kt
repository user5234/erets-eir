package game.eretseir.game


import android.graphics.Color
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import game.eretseir.BouncyRecyclerView
import game.eretseir.R

class GameSolutionsRecyclerAdapter(private val solutions : Collection<String>, private val submittedAnswers : MutableCollection<String>) : RecyclerView.Adapter<GameSolutionsRecyclerAdapter.ViewHolder>() {

    private val mData = mutableListOf(HolderData())
    private val recyclerViews = mutableListOf<RecyclerView>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.game_solutions_list_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val holderData = mData[position]
        holder.bindData(holderData)

        if (!holderData.isEditable)
            return

        holder.item.findViewById<EditText>(R.id.editText).apply {
            holderData.watcher = doOnTextChanged { textCharSequence, _, _, _ ->
                val text = textCharSequence.toString()
                solutions.forEach { solution ->
                    if (solution.parse() == text.parse() && solution !in submittedAnswers) {
                        submittedAnswers.add(solution)
                        holderData.let { it.isEditable = false; it.text = text }
                        holder.bindData(holderData)
                        mData.add(HolderData())
                        notifyItemInserted(itemCount)
                        recyclerViews.forEach { it.post { it.scrollToPosition(itemCount - 1) } }
                        return@doOnTextChanged
                    }
                }
            }
            requestFocus()
        }
    }

    override fun getItemCount(): Int = mData.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerViews.add(recyclerView)
    }

    private fun String.parse() = replace("-", " ").replace("'", "")

    //the ViewHolder class
    inner class ViewHolder(internal val item : View) : BouncyRecyclerView.ViewHolder(item) {

        internal fun bindData(data : HolderData) {
            item.findViewById<EditText>(R.id.editText).apply { removeTextChangedListener(data.watcher) }
            data.watcher = null
            item.findViewById<MaterialCardView>(R.id.cardView).strokeColor = if (data.isEditable) Color.RED else Color.GREEN
            item.findViewById<EditText>(R.id.editText).apply {
                isClickable = data.isEditable
                isFocusable = data.isEditable
                setText(data.text)
            }
        }
    }

    internal data class HolderData(var text : String = "", var isEditable: Boolean = true, var watcher: TextWatcher? = null)
}