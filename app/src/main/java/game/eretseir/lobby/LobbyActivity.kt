package game.eretseir.lobby

import android.content.Intent
import android.os.Bundle
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationUtils
import android.widget.Button
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ListenerRegistration
import game.eretseir.*
import game.eretseir.databinding.LettersWheelBinding
import game.eretseir.databinding.LobbyActivityBinding
import game.eretseir.game.GameActivity
import game.eretseir.home.addOnDisconnectListener
import game.eretseir.home.removeOnDisconnectListener
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LobbyActivity : FullScreenActivity() {

    companion object {
        lateinit var instance : LobbyActivity; private set
        lateinit var scope : LifecycleCoroutineScope
    }

    //this value tells whether this activity is from a game that is already running,
    //or that this is a lobby of a game that hasn't started
    private var isFromGame : Boolean = false
    private var rounds : Int = 0

    private lateinit var binding : LobbyActivityBinding
    private lateinit var gameCode : String
    private lateinit var userName : String
    private lateinit var admin : String
    private lateinit var game : Game
    private lateinit var disconnectListener : () -> Unit
    private lateinit var kickedListener : ListenerRegistration
    private lateinit var adminLeaveListener : ListenerRegistration
    private lateinit var gameStartListener : ListenerRegistration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        instance = this
        scope = lifecycleScope

        isFromGame = intent.extras?.get("isFromGame") as Boolean
        gameCode = intent.extras?.get("gameCode") as String
        userName = intent.extras?.get("userName") as String
        admin = intent.extras?.get("admin") as String
        rounds = intent.extras?.get("roundsLeft") as Int

        game = Game.fromExistingGame(gameCode)

        binding = LobbyActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.onlineButton.setOnClickListener { onBackPressed() }

        //not from existing game (was just created)
        if (!isFromGame) {
            binding.gameCodeTextView.text = getString(R.string.lobbyGameCode, gameCode)
            val recyclerAdapter = UsersRecyclerAdapter(admin)
            val players = recyclerAdapter.players
            players.add(userName)
            binding.usersRecyclerView.adapter = recyclerAdapter

            scope.launch {
                //when another player leaves or joins the lobby
                //the flow sends all the players when connecting to it so no need for
                //retrieving the players separately when entering the lobby
                //the flow will be cancelled when the activity finishes
                usersFlow(gameCode)
                    .cancellable()
                    .filter { it.first != userName }
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
                            if (removedElement == admin) {
                                cancelAsyncStuff()
                                showAlert(layoutInflater, binding.root, true, "המלך סגר את המשחק אחי", "חזור") {
                                    onBackPressed()
                                }
                                cancel()
                            }
                        }
                    }
            }
        }
        //is from existing game
        else {
            binding.gameCodeTextView.text = getString(R.string.roundsLeft, rounds.toString())
            val recyclerAdapter = UsersPointsRecyclerAdapter(admin, userName)
            val players = recyclerAdapter.players
            binding.usersRecyclerView.adapter = recyclerAdapter
            scope.launch {
                players.putAll(
                    game.playersFsRef
                        .get()
                        .await()
                        .documents
                        .filter { it.data != null }
                        .sortedByDescending { Game.PlayerData.fromMap(it.data!!).points }
                        .associate { it.id to Game.PlayerData.fromMap(it.data!!).points.toInt() }
                )
                //we have not submitted answers in time
                if (userName !in players.keys) {
                    cancelAsyncStuff()
                    showAlert(layoutInflater, binding.root, true, "מצטער אחי הייתה בעיה עם השרת", "חזור") {
                        onBackPressed()
                    }
                    return@launch
                }
                recyclerAdapter.notifyItemRangeInserted(0, players.size)
            }
        }

        if (admin == userName) {
            binding.waitForAdminTextView.visibility = INVISIBLE
            binding.startButton.apply {
                visibility = VISIBLE
                action = { showLettersWheel() }
            }
        }

        kickedListener = game.playersFsRef
            .whereEqualTo(FieldPath.documentId(), userName)
            .addSnapshotListener { value, error ->
                //an error occurred
                if (error != null) {
                    cancelAsyncStuff()
                    showAlert(layoutInflater, binding.root, true, "מצטער אחי הייתה בעיה עם השרת", "חזור") {
                        onBackPressed()
                    }
                    return@addSnapshotListener
                }
                //you were kicked
                if (value?.isEmpty == true) {
                    cancelAsyncStuff()
                    showAlert(layoutInflater, binding.root, true, "הקיקו אותך או שיש לך אינטרנט איטי ולכן הועפת", "חזור") {
                        onBackPressed()
                    }
                }
            }

        adminLeaveListener = game.playersFsRef
            .whereEqualTo(FieldPath.documentId(), admin)
            .addSnapshotListener { value, error ->
                //an error occurred
                if (error != null) {
                    cancelAsyncStuff()
                    showAlert(layoutInflater, binding.root, true, "מצטער אחי הייתה בעיה עם השרת", "חזור") {
                        onBackPressed()
                    }
                    return@addSnapshotListener
                }
                //the admin left
                if (value?.isEmpty == true) {
                    cancelAsyncStuff()
                    showAlert(layoutInflater, binding.root, true, "המלך סגר את המשחק אחי", "חזור") {
                        onBackPressed()
                    }
                }
            }

        gameStartListener = game
            .addSnapshotListener { snapshot, data, error ->
                //an error occurred
                if (error != null) {
                    cancelAsyncStuff()
                    showAlert(layoutInflater, binding.root, true, "מצטער אחי הייתה בעיה עם השרת", "חזור") {
                        onBackPressed()
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
                    enterGame(data.letter)
                }
        }

        disconnectListener = {
            cancelAsyncStuff()
            showAlert(layoutInflater, binding.root, true, "מצטער אחי אבל אין לך אינטרנט", "חזור") {
                onBackPressed()
            }
        }
        addOnDisconnectListener(true, disconnectListener)
    }

    private fun showLettersWheel() {
        LettersWheelBinding.inflate(layoutInflater, binding.root, true).apply {
            //animating the pop up
            val popUpAnim = AnimationUtils.loadAnimation(this@LobbyActivity, R.anim.view_popup)
            container.startAnimation(popUpAnim)
            root.startAnimation(AlphaAnimation(0F, 1F).apply { duration = 500 })
            //spin the wheel when clicking the button
            spinButton.setOnClickListener {
                it as Button
                val letter = lettersWheel.spin()
                lettersWheel.letters.remove(letter)
                it.setOnClickListener {  }
                it.postDelayed({
                    it.text = "התחל משחק"
                    it.setOnClickListener { game.startGame(letter) }
                }, 6000)
            }
        }
    }

    private fun enterGame(letter : String) {
        cancelAsyncStuff()
        finish()
        Intent(this, GameActivity::class.java).apply {
            putExtra("gameCode", gameCode)
            putExtra("userName", userName)
            putExtra("admin", admin)
            putExtra("letter", letter)
            putExtra("roundsLeft", rounds)
            startActivity(this)
            cancelAsyncStuff()
            finish()
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

            override fun onCancelled(error: DatabaseError) {
                //well fuck
            }
        }

        val usersReference = realtimeDatabase.getReference("/online/$gameCode/")
        usersReference.addChildEventListener(childListener)

        awaitClose { usersReference.removeEventListener(childListener) }
    }

    private fun cancelAsyncStuff() {
        removeOnDisconnectListener(disconnectListener)
        adminLeaveListener.remove()
        kickedListener.remove()
        gameStartListener.remove()
    }

    override fun onBackPressed() {
        game.removePlayer(userName)
        cancelAsyncStuff()
        finish()
        super.onBackPressed()
    }
}