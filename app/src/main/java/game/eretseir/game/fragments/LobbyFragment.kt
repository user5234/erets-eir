package game.eretseir.game.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE
import androidx.fragment.app.viewModels
import game.eretseir.R
import game.eretseir.databinding.LettersWheelBinding
import game.eretseir.databinding.LobbyFragmentBinding
import game.eretseir.game.activities.GameActivity
import game.eretseir.game.adapters.UsersPointsRecyclerAdapter
import game.eretseir.game.viewmodels.LobbyViewModel
import game.eretseir.game.viewmodels.PlayersViewModel
import game.eretseir.showAlert

class LobbyFragment(private val isFromGame : Boolean) : Fragment() {

    private val lobbyViewModel : LobbyViewModel by viewModels()
    private val playersViewModel : PlayersViewModel by viewModels()

    private lateinit var gameActivity : GameActivity
    private lateinit var binding : LobbyFragmentBinding

    override fun onAttach(context: Context) {
        super.onAttach(context)
        gameActivity = context as GameActivity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = LobbyFragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.onlineButton.setOnClickListener { gameActivity.onBackPressed() }

        //setting the text above the start button / wait for admin textView
        binding.gameCodeTextView.text = if (isFromGame) getString(R.string.roundsLeft, GameActivity.rounds.toString())
                                        else            getString(R.string.lobbyGameCode, GameActivity.gameCode)

        //show the start button if admin
        if (GameActivity.admin == GameActivity.userName) {
            binding.waitForAdminTextView.visibility = View.INVISIBLE
            binding.startButton.apply {
                visibility = View.VISIBLE
                action = { showLettersWheel() }
            }
        }

        val recyclerAdapter = UsersPointsRecyclerAdapter(GameActivity.admin, GameActivity.userName, isFromGame)
        binding.usersRecyclerView.adapter = recyclerAdapter
        val players = recyclerAdapter.players

        playersViewModel.allPlayers().observe(viewLifecycleOwner) { allPlayers ->
            //this method assumes the points haven't changed
            val existingPlayersMap = players.toMap()
            val addedPlayers = allPlayers.filterKeys { it !in existingPlayersMap }.mapValues { it.value.points }
            val removedPlayers = existingPlayersMap.filterKeys { it !in allPlayers }
            //add the added players to players and notify the adapter
            players.addAll(addedPlayers.map { it.key to it.value })
            recyclerAdapter.notifyItemRangeInserted(players.size - addedPlayers.size, addedPlayers.size)
            //get the indices of all the removed players
            val removedPlayerIndices = mutableListOf<Int>()
            players.forEachIndexed { i, pair -> if (pair.first in removedPlayers) removedPlayerIndices.add(i) }
            //remove the removed players from players and notify the adapter
            players.removeAll(removedPlayers.map { it.key to it.value })
            removedPlayerIndices.forEach { recyclerAdapter.notifyItemRemoved(it) }
        }

        lobbyViewModel.gameStarted().observe(viewLifecycleOwner) {
            //start the game fragment and remove this one
            parentFragmentManager.beginTransaction().setTransition(TRANSIT_FRAGMENT_FADE).replace(R.id.fragmentContainer, GameFragment()).commit()
        }

        //listen for when the admin leaves
        playersViewModel.player(GameActivity.admin).observe(viewLifecycleOwner) { data -> data ?: somethingBadHappened("מצטער אחי המלך עזב") }
        //listen for when you are kicked
        playersViewModel.player(GameActivity.admin).observe(viewLifecycleOwner) { data -> data ?: somethingBadHappened("מצטער אחי הייתה בעיה עם השרת") }

        //listen for errors
        lobbyViewModel.error().observe(viewLifecycleOwner) { error -> somethingBadHappened(error) }
        playersViewModel.error().observe(viewLifecycleOwner) { error -> somethingBadHappened(error) }
    }

    private fun somethingBadHappened(description: String) {
        //stop listening to the data from the view models
        lobbyViewModel.allLiveData().forEach { liveData -> liveData.removeObservers(viewLifecycleOwner) }
        playersViewModel.allLiveData().forEach { liveData -> liveData.removeObservers(viewLifecycleOwner) }
        //show an alert with the description
        showAlert(layoutInflater, binding.root, true, description, "חזור") { gameActivity.onBackPressed() }
    }

    private fun showLettersWheel() {
        LettersWheelBinding.inflate(layoutInflater, binding.root, true).apply {
            //animating the pop up
            val popUpAnim = AnimationUtils.loadAnimation(gameActivity, R.anim.view_popup)
            container.startAnimation(popUpAnim)
            root.startAnimation(AlphaAnimation(0F, 1F).apply { duration = 500 })
            lettersWheel.letters = GameActivity.lettersLeft
            //spin the wheel when clicking the button
            spinButton.setOnClickListener {
                it as Button
                val duration = 6000L
                GameActivity.letter = lettersWheel.spin(duration)
                lobbyViewModel.notifyNewLetter(GameActivity.letter)
                it.setOnClickListener {  }
                it.postDelayed({
                    it.text = "התחל משחק"
                    it.setOnClickListener { lobbyViewModel.notifyClickedStartGame() }
                }, duration)
            }
        }
    }
}
