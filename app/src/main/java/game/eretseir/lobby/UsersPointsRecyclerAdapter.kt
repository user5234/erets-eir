package game.eretseir.lobby

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import gal.libs.bouncyrecyclerview.BouncyRecyclerView
import game.eretseir.R

open class UsersPointsRecyclerAdapter(private val admin : String, private val userName : String) : RecyclerView.Adapter<UsersPointsRecyclerAdapter.ViewHolder>() {

    val players = mutableMapOf<String, Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder
            = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.users_points_list_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val userName = players.keys.toList()[position]
        holder.item.findViewById<TextView>(R.id.userNameTextView).text = userName
        holder.item.findViewById<TextView>(R.id.pointsTextView).text = "${players[userName]}"
        if (userName == this.userName)
            holder.item.findViewById<CardView>(R.id.cardView).setCardBackgroundColor(Color.rgb(50, 56, 68))
        if (userName == admin)
            holder.item.findViewById<View>(R.id.leaderCrownImageView).visibility = View.VISIBLE
    }

    override fun getItemCount(): Int = players.size

    //the ViewHolder class
    inner class ViewHolder(internal val item : View) : BouncyRecyclerView.ViewHolder(item)
}