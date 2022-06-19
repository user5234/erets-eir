package game.eretseir.game.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import gal.libs.bouncyrecyclerview.BouncyRecyclerView
import game.eretseir.R

class UsersRecyclerAdapter(private val admin : String, private val username : String) : RecyclerView.Adapter<UsersRecyclerAdapter.ViewHolder>() {

    val players = mutableListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder
        = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.users_list_item, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.item.findViewById<TextView>(R.id.userNameTextView).text = players[position]
        if (players[position] == username)
            holder.item.findViewById<CardView>(R.id.cardView).setCardBackgroundColor(Color.rgb(50, 56, 68))
        if (players[position] == admin)
            holder.item.findViewById<View>(R.id.leaderCrownImageView).visibility = VISIBLE
    }

    override fun getItemCount(): Int = players.size


    //the ViewHolder class
    inner class ViewHolder(internal val item : View) : BouncyRecyclerView.ViewHolder(item)
}