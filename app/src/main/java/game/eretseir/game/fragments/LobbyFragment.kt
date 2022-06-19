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
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ListenerRegistration
import game.eretseir.Game
import game.eretseir.R
import game.eretseir.databinding.LettersWheelBinding
import game.eretseir.databinding.LobbyFragmentBinding
import game.eretseir.game.activities.GameActivity
import game.eretseir.game.adapters.UsersPointsRecyclerAdapter
import game.eretseir.game.adapters.UsersRecyclerAdapter
import game.eretseir.realtimeDatabase
import game.eretseir.showAlert
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LobbyFragment : Fragment() {

    private lateinit var gameActivity : GameActivity
    private lateinit var binding : LobbyFragmentBinding
    private lateinit var kickedListener : ListenerRegistration
    private lateinit var adminLeaveListener : ListenerRegistration
    private lateinit var gameStartListener : ListenerRegistration

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

        //-------------------------------------------------------------------------adding players to the recyclerview-----------------------------------------------------
        //not from existing game (was just created)
        if (!GameActivity.isFromGame) {
            binding.gameCodeTextView.text = getString(R.string.lobbyGameCode, GameActivity.gameCode)
            val recyclerAdapter = UsersRecyclerAdapter(GameActivity.admin, GameActivity.userName)
            val players = recyclerAdapter.players
            players.add(GameActivity.userName)
            binding.usersRecyclerView.adapter = recyclerAdapter

            lifecycleScope.launch {
                //when another player leaves or joins the lobby
                //the flow sends all the players when connecting to it so no need for
                //retrieving the players separately when entering the lobby
                //the flow will be cancelled when the activity finishes
                usersFlow(GameActivity.gameCode)
                    .cancellable()
                    .filter { it.first != GameActivity.userName }
                    .collectLatest {
                        //a player joined
                        if (it.second) {
                            players.add(it.first)
                            recyclerAdapter.notifyItemInserted(players.lastIndex)
                        }
                        //a player left
                        else {
                            val removedIndex = players.indexOf(it.first)
                            val removedElement = players.removeAt(removedIndex)
                            recyclerAdapter.notifyItemRemoved(removedIndex)
                            //the admin left
                            if (removedElement == GameActivity.admin) {
                                cancelStuff()
                                showAlert(layoutInflater, binding.root, true, "המלך סגר את המשחק אחי", "חזור") {
                                    gameActivity.onBackPressed()
                                }
                                cancel()
                            }
                        }
                    }
            }
        }
        //is from existing game
        else {
            binding.gameCodeTextView.text = getString(R.string.roundsLeft, GameActivity.rounds.toString())
            val recyclerAdapter = UsersPointsRecyclerAdapter(GameActivity.admin, GameActivity.userName)
            val players = recyclerAdapter.players
            binding.usersRecyclerView.adapter = recyclerAdapter
            lifecycleScope.launch {
                players.putAll(
                    GameActivity.game.playersFsRef
                        .get()
                        .await()
                        .documents
                        .filter { it.data != null }
                        .sortedByDescending { Game.PlayerData.fromMap(it.data!!).points }
                        .associate { it.id to Game.PlayerData.fromMap(it.data!!).points.toInt() }
                )
                //we have not submitted answers in time
                if (GameActivity.userName !in players.keys) {
                    cancelStuff()
                    showAlert(layoutInflater, binding.root, true, "מצטער אחי הייתה בעיה עם השרת", "חזור") {
                        gameActivity.onBackPressed()
                    }
                    return@launch
                }
                recyclerAdapter.notifyItemRangeInserted(0, players.size)
            }
        }

        //show the start button if admin
        if (GameActivity.admin == GameActivity.userName) {
            binding.waitForAdminTextView.visibility = View.INVISIBLE
            binding.startButton.apply {
                visibility = View.VISIBLE
                action = { showLettersWheel() }
            }
        }

        //-------------------------------------------------------------------------------------listeners-----------------------------------------------------------------
        kickedListener = GameActivity.game.playersFsRef
            .whereEqualTo(FieldPath.documentId(), GameActivity.userName)
            .addSnapshotListener { value, error ->
                //an error occurred
                if (error != null) {
                    cancelStuff()
                    showAlert(layoutInflater, binding.root, true, "מצטער אחי הייתה בעיה עם השרת", "חזור") {
                        gameActivity.onBackPressed()
                    }
                    return@addSnapshotListener
                }
                //you were kicked
                if (value?.isEmpty == true) {
                    cancelStuff()
                    showAlert(layoutInflater, binding.root, true, "הקיקו אותך או שיש לך אינטרנט איטי ולכן הועפת", "חזור") {
                        gameActivity.onBackPressed()
                    }
                }
            }

        adminLeaveListener = GameActivity.game.playersFsRef
            .whereEqualTo(FieldPath.documentId(), GameActivity.admin)
            .addSnapshotListener { value, error ->
                //an error occurred
                if (error != null) {
                    cancelStuff()
                    showAlert(layoutInflater, binding.root, true, "מצטער אחי הייתה בעיה עם השרת", "חזור") {
                        gameActivity.onBackPressed()
                    }
                    return@addSnapshotListener
                }
                //the admin left
                if (value?.isEmpty == true) {
                    cancelStuff()
                    showAlert(layoutInflater, binding.root, true, "המלך סגר את המשחק אחי", "חזור") {
                        gameActivity.onBackPressed()
                    }
                }
            }

        gameStartListener = GameActivity.game
            .addSnapshotListener { snapshot, data, error ->
                //an error occurred
                if (error != null) {
                    cancelStuff()
                    showAlert(layoutInflater, binding.root, true, "מצטער אחי הייתה בעיה עם השרת", "חזור") {
                        gameActivity.onBackPressed()
                    }
                    return@addSnapshotListener
                }
                //idk something dumb happened
                if (snapshot == null || !snapshot.exists())
                    return@addSnapshotListener

                //firestore thinks its clever
                if (snapshot.metadata.isFromCache)
                    return@addSnapshotListener

                //firestore charged me for another read :(
                if (data!!.isRunning) {
                    parentFragmentManager.beginTransaction().setTransition(TRANSIT_FRAGMENT_FADE).replace(R.id.fragmentContainer, GameFragment()).commit()
                }
            }
    }

    private fun showLettersWheel() {
        LettersWheelBinding.inflate(layoutInflater, binding.root, true).apply {
            //animating the pop up
            val popUpAnim = AnimationUtils.loadAnimation(gameActivity, R.anim.view_popup)
            container.startAnimation(popUpAnim)
            root.startAnimation(AlphaAnimation(0F, 1F).apply { duration = 500 })
            lettersWheel.letters = gameActivity.lettersLeft
            //spin the wheel when clicking the button
            spinButton.setOnClickListener {
                it as Button
                GameActivity.letter = lettersWheel.spin()
                gameActivity.lettersLeft.remove(GameActivity.letter)
                it.setOnClickListener {  }
                it.postDelayed({
                    it.text = "התחל משחק"
                    it.setOnClickListener { GameActivity.game.startGame(GameActivity.letter) } // will cause the start of the game from the gameStartListener
                }, 6000)
            }
        }
    }

    /**
     * sends a pair of the player name, to - true if he was added, false if he was removed
     */
    private fun usersFlow(gameCode : String) = callbackFlow {

        val childListener = object : ChildEventListener {

            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                trySend(snapshot.key!! to true)
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                trySend(snapshot.key!! to false)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        val usersReference = realtimeDatabase.getReference("/online/$gameCode/")
        usersReference.addChildEventListener(childListener)

        awaitClose { usersReference.removeEventListener(childListener) }
    }

    private fun cancelStuff() {
        adminLeaveListener.remove()
        kickedListener.remove()
        gameStartListener.remove()
    }

    override fun onDestroy() {
        cancelStuff()
        super.onDestroy()
    }
}