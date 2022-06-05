package game.eretseir.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.generateViewId
import android.view.animation.AnimationUtils
import android.widget.HorizontalScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ListenerRegistration
import game.eretseir.*
import game.eretseir.databinding.GameActivityBinding
import game.eretseir.databinding.GameSolutionsRowBinding
import game.eretseir.home.addOnDisconnectListener
import game.eretseir.home.removeOnDisconnectListener
import game.eretseir.lobby.LobbyActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.math.max

private const val playTime = 1000
private const val countdownSeconds = 3

class GameActivity : FullScreenActivity() {

    companion object {
        lateinit var instance : GameActivity; private set
        lateinit var scope : LifecycleCoroutineScope
    }

    private var playerSolutions = Game.SolutionsData() //the solutions the player will send
    private var rounds : Int = 0

    private lateinit var binding : GameActivityBinding
    private lateinit var gameCode : String
    private lateinit var userName : String
    private lateinit var admin : String
    private lateinit var letter : String
    private lateinit var game : Game
    private lateinit var timer: Timer
    private lateinit var disconnectListener : () -> Unit
    private lateinit var adminLeaveListener : ListenerRegistration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        instance = this
        scope = lifecycleScope

        gameCode = intent.extras?.get("gameCode") as String
        userName = intent.extras?.get("userName") as String
        admin = intent.extras?.get("admin") as String
        letter = intent.extras?.get("letter") as String
        rounds = intent.extras?.get("roundsLeft") as Int

        game = Game.fromExistingGame(gameCode)

        binding = GameActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.entryScreenLetter.text = getString(R.string.gameLetter, letter)
        binding.infoLetter.text = letter

        scope.launch {
            var allSolutionsMap = mapOf<String, MutableList<String>>()
            val result = withTimeoutOrNull(countdownSeconds * 1000L) {
                allSolutionsMap = game.getSolutions(letter).toMapHebrew()
            }
            result ?: run {
                cancelAsyncStuff()
                showAlert(layoutInflater, binding.root, true, "מצטער אחי לקח יותר מדי זמן לשרת להגיב", "חזור") {
                    onBackPressed()
                }
                return@launch
            }
            var prevId: Int? = null
            for (i in playerSolutions.toMapHebrew().entries) {
                GameSolutionsRowBinding.inflate(layoutInflater, binding.gameSolutions, true).apply {
                    recyclerView.adapter = GameSolutionsRecyclerAdapter(allSolutionsMap[i.key]!!, i.value)
                    textView.text = i.key
                    val params = root.layoutParams as ConstraintLayout.LayoutParams
                    params.width = max(spToPx(170F, this@GameActivity), binding.scrollView.width / 8F).toInt()
                    if (prevId != null)
                        params.startToEnd = prevId!!
                    else
                        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    root.requestLayout()
                    root.id = generateViewId()
                    prevId = root.id
                }
            }
            binding.scrollView.doOnLayout { (it as HorizontalScrollView).fullScroll(View.FOCUS_RIGHT) }
        }

        //if the admin leaves the game show an alert
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

        //first 3 seconds are the countdown in the beginning,
        //after that the next 30 seconds are for the game countdown,
        //and after them 2 seconds where the player sends his answers and a screen is shown
        //in a run call to encapsulate the seconds var and the countdown animation
        run {
            var seconds = playTime + countdownSeconds + 2
            val countDownAnim = AnimationUtils.loadAnimation(this, R.anim.countdown)
            timer = Timer().apply {
                scheduleAtFixedRate(timerTask {
                    seconds--
                    //showing the countdown for the start
                    if (seconds > playTime) {
                        runOnUiThread {
                            binding.entryScreenCountdown.text = if (seconds == playTime + 1) "GO" else "${seconds - playTime - 1}"
                            binding.entryScreenCountdown.startAnimation(countDownAnim)
                        }
                        //show countdown before game starts
                        return@timerTask
                    }
                    if (seconds == playTime) {
                        runOnUiThread { binding.root.removeWithAnimation(binding.entryScreen) }
                    }
                    if (seconds == 0) {
                        //show ending screen
                        binding.root.let { it.addWithAnimation(layoutInflater.inflate(R.layout.game_end_screen, it, false)) }
                        closeKeyboard()
                        runOnUiThread { binding.root.requestFocus() }
                        //finish the game and send the data
                        game.sendSolutions(userName, playerSolutions)
                        //if admin, eng the game and wait 1 second for everyone to send their solutions
                        //after that, give everyone points
                        if (admin == userName) {
                            scope.launch {
                                game.endGame().await()
                                delay(1000)
                                game.calculatePoints()
                            }
                        }
                    }
                    if (seconds == -3) {
                        cancel()
                        //this was the last round
                        if (rounds == 1) {
                            Intent(this@GameActivity, GameEndActivity::class.java).apply {
                                putExtra("gameCode", gameCode)
                                putExtra("userName", userName)
                                putExtra("admin", admin)
                                startActivity(this)
                                cancelAsyncStuff()
                                finish()
                            }
                            return@timerTask
                        }
                        //there are still some rounds left
                        Intent(this@GameActivity, LobbyActivity::class.java).apply {
                            putExtra("gameCode", gameCode)
                            putExtra("userName", userName)
                            putExtra("admin", admin)
                            putExtra("roundsLeft", rounds - 1)
                            putExtra("isFromGame", true)
                            startActivity(this)
                            cancelAsyncStuff()
                            finish()
                        }
                    }
                    //show countdown while game is running
                    runOnUiThread { binding.infoTimer.text = "$seconds" }
                }, 1000, 1000)
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

    private fun cancelAsyncStuff() {
        removeOnDisconnectListener(disconnectListener)
        adminLeaveListener.remove()
        timer.cancel()
    }

    override fun onBackPressed() {
        cancelAsyncStuff()
        game.removePlayer(userName)
        finish()
        super.onBackPressed()
    }
}