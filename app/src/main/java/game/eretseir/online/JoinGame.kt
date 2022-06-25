package game.eretseir.online

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnLayout
import game.eretseir.Game
import game.eretseir.R
import game.eretseir.game.activities.GameActivity
import game.eretseir.home.connectedToRTDB
import game.eretseir.removeWithAnimation
import kotlinx.coroutines.launch

class JoinGame : ConstraintLayout {

    private val onlineActivity = context as OnlineActivity
    private val scope = onlineActivity.scope

    constructor(ctx: Context) : super(ctx)

    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int) : super(ctx, attrs, defStyleAttr)

    init {
        doOnLayout {
            //remove this view when clicking the return button
            findViewById<ImageButton>(R.id.returnImageButton).setOnClickListener{
                (parent as ViewGroup).removeWithAnimation(this@JoinGame)
                //when the view is shown the home button is disabled, so we need to enable it when removing this view
                onlineActivity.binding.homeButton.isClickable = true
            }
            //when clicking the button it checks if the game exists and is joinable and does shit accordingly
            findViewById<Button>(R.id.startButton).setOnClickListener{ joinLobby() }
        }
    }

    private fun joinLobby() {
        /**
         * show an error message in case the user can't join
         */
        fun showError(errorView: TextView, error : String) = post {
            errorView.apply {
                background = ResourcesCompat.getDrawable(context.resources, R.drawable.single_border_red, null)
                text = ""
                hint = error
            }
            //show the button and hide the progress bar
            findViewById<View>(R.id.startButton).apply { visibility = VISIBLE; setOnClickListener { joinLobby() } }
            findViewById<View>(R.id.startProgressBar).visibility = INVISIBLE
        }
        //now checking
        val userNameEditText = findViewById<EditText>(R.id.userNameEditText)
        val userName = userNameEditText.text.toString()
        if (userName.length < 4) {
            showError(userNameEditText, "תכניס שם ארוך יותר")
            return
        }
        //set a loading animation until the game is found (or not)
        findViewById<View>(R.id.startButton).apply { visibility = INVISIBLE; isClickable = false }
        findViewById<View>(R.id.startProgressBar).visibility = VISIBLE
        //find the game and if it exists and is joinable than join, otherwise let the user try again
        val gameCode = findViewById<EditText>(R.id.gameCodeEditText).text.toString()
        //now the real shit
        scope.launch {
            if (!connectedToRTDB.value!!)
                return@launch
            //game doesn't exist
            if (!Game.exists(gameCode)) {
                showError(userNameEditText, "המשחק לא קיים אחי")
                return@launch
            }
            val game = Game.fromExistingGame(gameCode)
            game.bringUpToDate()
            if (game.data!!.isRunning) {
                showError(userNameEditText, "התחילו בלעדיך אחי")
                return@launch
            }
            val players = game.getPlayersNames()
            //game is full
            if (players.size >= game.data!!.maxPlayers) {
                showError(userNameEditText, "המשחק מלא כבר אחי")
                return@launch
            }
            //name is taken
            if (userName in players) {
                showError(userNameEditText, "תפסו לך את השם אחי")
                return@launch
            }
            //game is joinable
            game.addPlayer(userName)
            //go to lobby
            post {
                findViewById<View>(R.id.returnImageButton).performClick()
                GameActivity.gameCode = gameCode
                GameActivity.userName = userName
                GameActivity.admin = game.data!!.admin
                GameActivity.rounds = game.data!!.rounds.toInt()
                GameActivity.isFromGame = false
                context.startActivity(Intent(context, GameActivity::class.java))
            }
        }
    }
}