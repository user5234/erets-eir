package game.eretseir.game.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import game.eretseir.game.activities.GameActivity

class LobbyViewModel : ViewModel() {

    val lettersLeft = mutableListOf("א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ", "ק", "ר", "ש", "ת")
    private lateinit var letter: String

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
            if (snapshot!!.metadata.isFromCache)
                return@addSnapshotListener
            //firestore charged me for another read :(
            if (data!!.isRunning)
                gameStarted.value = Unit
    }

    //--------------------------------------------------public LiveData accessor functions--------------------------------------------------------------
    fun gameStarted() : LiveData<Unit> = gameStarted
    fun error() : LiveData<String> = error

    fun allLiveData() : List<LiveData<*>> = listOf(gameStarted, error)
    //--------------------------------------------------------------------------------------------------------------------------------------------------

    fun notifyNewLetter(letter : String) { lettersLeft.remove(letter); this.letter = letter }

    fun notifyClickedStartGame() = GameActivity.game.startGame(letter) // will cause the start of the game from the gameStartedListener

    override fun onCleared() {
        gameStartedListener.remove()
        super.onCleared()
    }
}