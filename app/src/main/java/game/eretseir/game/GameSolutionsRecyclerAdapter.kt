package game.eretseir.game


import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import game.eretseir.BouncyRecyclerView
import game.eretseir.R

class GameSolutionsRecyclerAdapter(private val solutions : Collection<String>, private val submittedAnswers : MutableCollection<String>) : RecyclerView.Adapter<GameSolutionsRecyclerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.game_solutions_list_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        if (position != 0)
            return

        holder.item.findViewById<EditText>(R.id.editText).apply {
            doOnTextChanged { textCharSequence, _, _, _ ->
                val text = textCharSequence.toString()
                solutions.forEach { solution ->
                    if (solution.parse() == text.parse() && solution !in submittedAnswers) {
                        (parent as MaterialCardView).strokeColor = Color.GREEN
                        submittedAnswers.add(solution)
                        isClickable = false
                        isFocusable = false
                        setText(text)
                        notifyItemInserted(0)
                        return@doOnTextChanged
                    }
                }
            }
            requestFocus()
        }
    }

    override fun getItemCount(): Int = submittedAnswers.size + 1

    private fun String.parse() = replace("-", " ").replace("'", "")

    //the ViewHolder class
    inner class ViewHolder(internal val item : View) : BouncyRecyclerView.ViewHolder(item)
}