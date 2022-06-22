package game.eretseir.game.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import gal.libs.bouncyrecyclerview.BouncyRecyclerView
import game.eretseir.R

open class UsersPointsRecyclerAdapter(private val admin : String, private val userName : String, private val showPoints : Boolean) : RecyclerView.Adapter<UsersPointsRecyclerAdapter.ViewHolder>() {

    /**
     * list of pairs instead of map for indexing
     */
    val players = mutableListOf<Pair<String, Long>>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = if (showPoints) R.layout.users_points_list_item else R.layout.users_list_item
        val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val userName = players[position].first
        holder.item.findViewById<TextView>(R.id.userNameTextView).text = userName
        if (showPoints)
            holder.item.findViewById<TextView>(R.id.pointsTextView).text = "${players[position].second}"
        if (userName == this.userName)
            holder.item.findViewById<CardView>(R.id.cardView).setCardBackgroundColor(Color.rgb(50, 56, 68))
        if (userName == admin)
            holder.item.findViewById<View>(R.id.leaderCrownImageView).visibility = View.VISIBLE
    }

    override fun getItemCount(): Int = players.size

    //the ViewHolder class
    inner class ViewHolder(internal val item : View) : BouncyRecyclerView.ViewHolder(item)
}