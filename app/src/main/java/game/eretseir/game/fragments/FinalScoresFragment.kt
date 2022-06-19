package game.eretseir.game.fragments

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import game.eretseir.Game
import game.eretseir.R
import game.eretseir.databinding.FinalScoresFragmentBinding
import game.eretseir.game.activities.GameActivity
import game.eretseir.game.adapters.UsersPointsRecyclerAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Spread
import nl.dionsegijn.konfetti.core.emitter.Emitter
import java.util.concurrent.TimeUnit

class FinalScoresFragment : Fragment() {

    private lateinit var gameActivity : GameActivity
    private lateinit var binding : FinalScoresFragmentBinding

    override fun onAttach(context: Context) {
        super.onAttach(context)
        gameActivity = context as GameActivity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FinalScoresFragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerAdapter = UsersPointsRecyclerAdapter(GameActivity.admin, GameActivity.userName)
        val players = recyclerAdapter.players
        binding.usersRecyclerView.adapter = recyclerAdapter

        lifecycleScope.launch {
            var bestPlayer : Pair<String, Long>
            val flow = GameActivity.game.playersFsRef
                .get()
                .await()
                .documents
                .filter { it.data != null }
                .toMutableList()
                .apply {
                    sortBy { Game.PlayerData.fromMap(it.data!!).points }
                    bestPlayer = last().id to Game.PlayerData.fromMap(last().data!!).points
                    removeLast()
                }
                .map { it.id to Game.PlayerData.fromMap(it.data!!).points.toInt() }
                .asFlow()
            var index = 0
            //animate the winner
            binding.winnerPlayer.root.apply {
                visibility = View.VISIBLE
                findViewById<TextView>(R.id.userNameTextView).text = bestPlayer.first
                findViewById<TextView>(R.id.pointsTextView).text = "${bestPlayer.second}"
                findViewById<CardView>(R.id.cardView).setCardBackgroundColor(Color.rgb(229, 184, 11))
                if (bestPlayer.first == GameActivity.admin)
                    findViewById<View>(R.id.leaderCrownImageView).visibility = View.VISIBLE
                startAnimation(AnimationUtils.loadAnimation(gameActivity, R.anim.winner_animation))
            }
            //add the other players
            flow.collect {
                delay(1000)
                players[it.first] = it.second
                recyclerAdapter.notifyItemInserted(index)
                binding.usersRecyclerView.scrollToPosition(index)
                index++
            }
            if (bestPlayer.first != GameActivity.userName)
                return@launch
            //weeeee are the championsssss
            //start konfetti
            delay(binding.winnerPlayer.root.animation.duration)
            binding.konfettiView1.start(
                Party(
                    angle = 335,
                    spread = Spread.SMALL,
                    speed = 10F,
                    maxSpeed = 50F,
                    position = Position.Relative(0.0, 0.7),
                    emitter = Emitter(duration = 5, TimeUnit.SECONDS).perSecond(80)
                )
            )
            binding.konfettiView2.start(
                Party(
                    angle = 215,
                    spread = Spread.SMALL,
                    speed = 10F,
                    maxSpeed = 50F,
                    position = Position.Relative(1.0, 0.7),
                    emitter = Emitter(duration = 5, TimeUnit.SECONDS).perSecond(80)
                )
            )
        }
    }
}