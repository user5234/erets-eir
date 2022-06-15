package game.eretseir.game.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ListenerRegistration
import game.eretseir.*
import game.eretseir.databinding.GameFragmentBinding
import game.eretseir.databinding.GameSolutionsRowBinding
import game.eretseir.game.GameActivity
import game.eretseir.game.adapters.GameSolutionsRecyclerAdapter
import game.eretseir.home.addOnDisconnectListener
import game.eretseir.home.removeOnDisconnectListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.math.max


class GameFragment : Fragment() {

    private val playtime = 60
    private val countdownSeconds = 3

    private var playerSolutions = Game.SolutionsData() //the solutions the player will send

    private lateinit var gameActivity : GameActivity
    private lateinit var binding : GameFragmentBinding
    private lateinit var timer: Timer
    private lateinit var disconnectListener : () -> Unit
    private lateinit var adminLeaveListener : ListenerRegistration

    override fun onAttach(context: Context) {
        super.onAttach(context)
        gameActivity = context as GameActivity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = GameFragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    /**
     * All the interesting stuff
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.entryScreenLetter.text = getString(R.string.gameLetter, gameActivity.letter)
        binding.infoLetter.text = gameActivity.letter

        //Fetching the solutions from firestore and creating the rows for the game
        lifecycleScope.launch {
            var allSolutionsMap = mapOf<String, MutableList<String>>()
            val result = withTimeoutOrNull(countdownSeconds * 1000L) {
                allSolutionsMap = gameActivity.game.getSolutions(gameActivity.letter).toMapHebrew()
            }
            //If we timed out kick the player
            result ?: run {
                cancelStuff()
                showAlert(layoutInflater, binding.root, true, "מצטער אחי לקח יותר מדי זמן לשרת להגיב", "חזור") {
                    gameActivity.onBackPressed()
                }
                return@launch
            }
            //Creating the rows for the game
            var prevId: Int? = null
            for (i in playerSolutions.toMapHebrew().entries) {
                GameSolutionsRowBinding.inflate(layoutInflater, binding.gameSolutions, true).apply {
                    recyclerView.adapter = GameSolutionsRecyclerAdapter(allSolutionsMap[i.key]!!, i.value)
                    textView.text = i.key
                    val params = root.layoutParams as ConstraintLayout.LayoutParams
                    params.width = max(spToPx(170F, gameActivity), binding.scrollView.width / 8F).toInt()
                    if (prevId != null)
                        params.startToEnd = prevId!!
                    else
                        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    root.requestLayout()
                    root.id = View.generateViewId()
                    prevId = root.id
                }
            }
            binding.scrollView.doOnLayout { (it as HorizontalScrollView).fullScroll(View.FOCUS_RIGHT) }
        }

        //if the admin leaves the game show an alert
        adminLeaveListener = gameActivity.game.playersFsRef
            .whereEqualTo(FieldPath.documentId(), gameActivity.admin)
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

        //first 3 seconds are the countdown in the beginning,
        //after that the next 30 seconds are for the game countdown,
        //and after them 2 seconds where the player sends his answers and a screen is shown
        //in a run call to encapsulate the seconds var and the countdown animation
        var seconds = playtime + countdownSeconds + 2
        val countDownAnim = AnimationUtils.loadAnimation(gameActivity, R.anim.countdown)
        timer = Timer().apply {
            scheduleAtFixedRate(timerTask {
                seconds--
                //update the countdown timer
                view.post { binding.infoTimer.text = "$seconds" }
                //showing the countdown for the entry screen
                if (seconds > playtime) {
                    view.post {
                        binding.entryScreenCountdown.text = if (seconds == playtime + 1) "GO" else "${seconds - playtime - 1}"
                        binding.entryScreenCountdown.startAnimation(countDownAnim)
                    }
                }
                //starting
                if (seconds == playtime) {
                    view.post { binding.root.removeWithAnimation(binding.entryScreen) }
                }
                //game ended
                if (seconds == 0) {
                    //show ending screen
                    binding.root.let { it.addWithAnimation(layoutInflater.inflate(R.layout.game_end_screen, it, false)) }
                    gameActivity.closeKeyboard()
                    view.post { binding.root.requestFocus() }
                    //finish the game and send the data
                    gameActivity.game.sendSolutions(gameActivity.userName, playerSolutions)
                    //if admin, eng the game and wait 1 second for everyone to send their solutions
                    //after that, give everyone points
                    if (gameActivity.admin == gameActivity.userName) {
                        lifecycleScope.launch {
                            gameActivity.game.endGame().await()
                            delay(1000)
                            gameActivity.game.calculatePoints()
                        }
                    }
                }
                //go to lobby / final scores
                if (seconds == -3) {
                    cancel()
                    //this was the last round
                    if (gameActivity.rounds == 1) {
                        parentFragmentManager.beginTransaction().setTransition(TRANSIT_FRAGMENT_FADE).replace(R.id.fragmentContainer, FinalScoresFragment()).commit()
                        return@timerTask
                    }
                    //there are still some rounds left
                    parentFragmentManager.beginTransaction().setTransition(TRANSIT_FRAGMENT_FADE).replace(R.id.fragmentContainer, LobbyFragment()).commit()
                }
            }, 1000, 1000)
        }

        disconnectListener = {
            cancelStuff()
            showAlert(layoutInflater, binding.root, true, "מצטער אחי אבל אין לך אינטרנט", "חזור") {
                gameActivity.onBackPressed()
            }
        }
        addOnDisconnectListener(true, disconnectListener)
    }

    private fun cancelStuff() {
        removeOnDisconnectListener(disconnectListener)
        adminLeaveListener.remove()
        timer.cancel()
    }

    override fun onDestroy() {
        cancelStuff()
        super.onDestroy()
    }
}