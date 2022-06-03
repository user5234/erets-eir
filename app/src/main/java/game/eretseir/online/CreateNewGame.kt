package game.eretseir.online

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnLayout
import game.eretseir.BetterRadioGroup
import game.eretseir.Game
import game.eretseir.R
import game.eretseir.databinding.RadioButtonBinding
import game.eretseir.home.connectedToRTDB
import game.eretseir.lobby.LobbyActivity
import game.eretseir.removeWithAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

private fun randomString(size: Int = 6): String {
    val letters = ('A'..'Z').toList()
    return (0 until size)
        .toList()
        .map { Random(System.nanoTime()).nextInt(0, letters.size) }
        .map { i -> letters[i] }
        .joinToString("")
}

class CreateNewGame : ConstraintLayout {

    private val scope = CoroutineScope(Dispatchers.IO) //OnlineActivity.scope

    private lateinit var gameCode: String

    constructor(ctx: Context) : super(ctx)

    constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

    constructor(ctx: Context, attrs: AttributeSet, defStyleAttr: Int) : super(ctx, attrs, defStyleAttr)

    init {
        if (!isInEditMode)
            doOnLayout {
                //adds the radio buttons for the max players
                addRadioButtons()
                //remove this view when clicking the return button
                findViewById<View>(R.id.returnImageButton).setOnClickListener {
                    (parent as ViewGroup).removeWithAnimation(this)
                    //when the view is shown the home button is disabled, so we need to enable it when removing this view
                    OnlineActivity.instance.binding.homeButton.isClickable = true
                }
                //generates a new game code that doesn't exist in the database,
                //and makes the button visible when succeeded and give it a click listener
                scope.launch { gameCode = findGameAndConnect() }
            }
    }

    private suspend fun findGameAndConnect(): String {
        val code = randomString()
        if (Game.exists(code))
            return findGameAndConnect()
        post {
            findViewById<Button>(R.id.startButton).apply { visibility = VISIBLE; setOnClickListener { createGameAndJoinLobby() } }
            findViewById<ProgressBar>(R.id.startProgressBar).visibility = INVISIBLE
        }
        return code
    }

    private fun addRadioButtons() {
        val inflater = OnlineActivity.instance.layoutInflater
        val maxPlayers = findViewById<BetterRadioGroup>(R.id.maxPlayersRadioGroup)
        val gamesAmount = findViewById<BetterRadioGroup>(R.id.roundsAmountRadioGroup)
        for (i in 2..6)
            (RadioButtonBinding.inflate(inflater, maxPlayers, true)).apply {
                radioButton.text = "$i"
                if (i == 6) radioButton.isChecked = true
            }
        for (i in 1..7)
            (RadioButtonBinding.inflate(inflater, gamesAmount, true)).apply {
                radioButton.text = "$i"
                if (i == 7) radioButton.isChecked = true
            }
    }

    private fun createGameAndJoinLobby() {
        val userNameEditText = findViewById<EditText>(R.id.userNameEditText)
        val userName = userNameEditText.text.toString()
        if (userName.length < 4) {
            userNameEditText.background = ResourcesCompat.getDrawable(context.resources, R.drawable.single_border_red, null)
            userNameEditText.setText("")
            userNameEditText.hint = "תכניס שם ארוך יותר"
            return
        }
        val maxPlayers = findViewById<BetterRadioGroup>(R.id.maxPlayersRadioGroup).checkedRadioButton!!.text.toString().toLong()
        val rounds = findViewById<BetterRadioGroup>(R.id.roundsAmountRadioGroup).checkedRadioButton!!.text.toString().toLong()
        //set a loading animation until we go to the lobby
        findViewById<View>(R.id.startButton).apply { visibility = INVISIBLE; isClickable = false }
        findViewById<View>(R.id.startProgressBar).visibility = VISIBLE
        //if and when connected create the document on firebase
        scope.launch {
            if (!connectedToRTDB)
                return@launch
            Game.createGame(gameCode, "א", maxPlayers, rounds, userName)
            //go to the lobby activity
            post {
                findViewById<View>(R.id.returnImageButton).performClick()
                Intent(context, LobbyActivity::class.java).apply {
                    putExtra("gameCode", gameCode)
                    putExtra("userName", userName)
                    putExtra("admin", userName)
                    putExtra("roundsLeft", rounds.toInt())
                    putExtra("isFromGame", false)
                    OnlineActivity.instance.startActivity(this)
                }
            }
        }
    }
}