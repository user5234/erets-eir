package game.eretseir.game.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import game.eretseir.game.activities.GameActivity

class LobbyViewModel : ViewModel() {

    //----------------------------------------------------private MutableLiveData objects---------------------------------------------------------------
    private val gameStarted : MutableLiveData<Unit> by lazy { MutableLiveData<Unit>() }
    private val error : MutableLiveData<String> by lazy { MutableLiveData<String>() }
    private val gameStartedListener = GameActivity.game
        .addSnapshotListener { snapshot, data, error ->
            if (error != null) {
                this.error.value = "מצטער אחי הייתה בעיה עם השרת"
                return@addSnapshotListener
            }
            //firestore thinks its clever
            if (snapshot?.metadata?.isFromCache == true)
                return@addSnapshotListener
            //firestore charged me for another read :(
            if (data?.isRunning == true)
                gameStarted.value = Unit
    }

    //--------------------------------------------------public LiveData accessor functions--------------------------------------------------------------
    fun gameStarted() : LiveData<Unit> = gameStarted
    fun error() : LiveData<String> = error

    fun allLiveData() : List<LiveData<*>> = listOf(gameStarted, error)
    //--------------------------------------------------------------------------------------------------------------------------------------------------

    fun notifyNewLetter(letter : String) { GameActivity.lettersLeft.remove(letter); GameActivity.letter = letter }

    fun notifyClickedStartGame() = GameActivity.game.startGame(GameActivity.letter) // will cause the start of the game from the gameStartedListener

    override fun onCleared() {
        gameStartedListener.remove()
        super.onCleared()
    }
}