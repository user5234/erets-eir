package game.eretseir.game.activities

import android.os.Bundle
import gal.libs.fullscreenactivity.FullScreenActivity
import game.eretseir.Game
import game.eretseir.R

class GameActivity : FullScreenActivity() {

    val lettersLeft = mutableListOf("א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ", "ק", "ר", "ש", "ת")

    companion object {
        var rounds : Int = 0
        var isFromGame = false
        var letter : String = "א"
        lateinit var gameCode : String
        lateinit var userName : String
        lateinit var admin : String
        lateinit var game : Game
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isFromGame = intent.extras?.get("isFromGame") as Boolean
        userName = intent.extras?.get("userName") as String
        admin = intent.extras?.get("admin") as String
        rounds = intent.extras?.get("roundsLeft") as Int

        gameCode = intent.extras?.get("gameCode") as String
        game = Game.fromExistingGame(gameCode)

        setContentView(R.layout.game_activity)
    }

    override fun onBackPressed() {
        game.removePlayer(userName)
        finish()
        super.onBackPressed()
    }
}