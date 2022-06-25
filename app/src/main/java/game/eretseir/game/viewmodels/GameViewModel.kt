package game.eretseir.game.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import game.eretseir.Game
import game.eretseir.game.activities.GameActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import kotlin.concurrent.timerTask
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class GameViewModel : ViewModel() {

    //the solutions that the player fills, which will be sent to the database
    val playerSolutions = Game.SolutionsData()
    val playtime = 5

    private val countdownSeconds = 3
    private val timer : Timer

    //----------------------------------------------------private MutableLiveData objects---------------------------------------------------------------
    //all the solutions from the database
    private val allSolutions : MutableLiveData<Map<String, MutableList<String>>> by lazy { MutableLiveData<Map<String, MutableList<String>>>() }
    //the seconds until the game ends
    private val secondsLeft : MutableLiveData<Int> by lazy { MutableLiveData<Int>(playtime + countdownSeconds + 1) }
    //if an error occurs
    private val error : MutableLiveData<String> by lazy { MutableLiveData<String>() }
    //--------------------------------------------------public LiveData accessor functions--------------------------------------------------------------
    fun allSolutions() : LiveData<Map<String, MutableList<String>>> = allSolutions
    fun secondsLeft() : LiveData<Int> = secondsLeft
    fun error() : LiveData<String> = error

    fun allLiveData() : List<LiveData<out Any>> = listOf(allSolutions(), secondsLeft(), error())

    init {
        GameActivity.rounds--
        //get all the solutions from the database
        viewModelScope.launch {
            withTimeoutOrNull(countdownSeconds.toDuration(DurationUnit.SECONDS)) {
                allSolutions.value = GameActivity.game.getSolutions(GameActivity.letter).toMapHebrew()
            } ?: run {
                error.value = "מצטער אחי לקח יותר מדי זמן לשרת להגיב"
            }
        }
        timer = Timer().apply {
            scheduleAtFixedRate(timerTask {
                secondsLeft.postValue(secondsLeft.value!! - 1)
                //game ended
                if (secondsLeft.value == 0) {
                    //finish the game and send the data
                    GameActivity.game.sendSolutions(GameActivity.userName, playerSolutions)
                    //if admin, eng the game and wait 1 second for everyone to send their solutions
                    //after that, give everyone points
                    if (GameActivity.admin == GameActivity.userName) {
                        viewModelScope.launch {
                            GameActivity.game.endGame().await()
                            delay(1000)
                            GameActivity.game.calculatePoints()
                        }
                    }
                }
                //go to lobby / final scores
                if (secondsLeft.value == -3)
                    cancel()
            }, 1000, 1000)
        }
    }

    override fun onCleared() {
        timer.cancel()
        super.onCleared()
    }
}