package game.eretseir.game.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.ListenerRegistration
import game.eretseir.Game
import game.eretseir.game.activities.GameActivity
import kotlinx.coroutines.tasks.await

class PlayersViewModel : ViewModel() {

    //----------------------------------------------------private MutableLiveData objects---------------------------------------------------------------
    private val listeners = mutableListOf<ListenerRegistration>()
    private val allLiveData = mutableListOf<LiveData<*>>()
    private val error : MutableLiveData<String> by lazy { MutableLiveData<String>() }

    fun error() : LiveData<String> = error

    fun allLiveData() : List<LiveData<*>> = mutableListOf<LiveData<*>>().apply { addAll(allLiveData); add(error()) }

    fun player(player : String) : LiveData<Game.PlayerData?> { //if PlayerData is null the player is not in the game
        val liveData = MutableLiveData<Game.PlayerData?>()
        val listener = GameActivity.game.playersFsRef
            .whereEqualTo(FieldPath.documentId(), player)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    this.error.value = "מצטער אחי הייתה בעיה עם השרת"
                    return@addSnapshotListener
                }
                if (value == null)
                    return@addSnapshotListener
                if (value.isEmpty) {
                    liveData.value = null
                    return@addSnapshotListener
                }
                liveData.value = Game.PlayerData.fromMap(value.documents[0].data!!)
            }
        allLiveData.add(liveData)
        listeners.add(listener)
        return liveData
    }

    suspend fun playerOnce(player : String) : Game.PlayerData? =
        GameActivity.game.playersFsRef
            .document(player)
            .get()
            .await()
            .data?.let { Game.PlayerData.fromMap(it) }

    fun players(players : List<String>) : LiveData<Map<String, Game.PlayerData>> {
        val liveData = MutableLiveData<Map<String, Game.PlayerData>>()
        val listener = GameActivity.game.playersFsRef
            .whereIn(FieldPath.documentId(), players)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    this.error.value = "מצטער אחי הייתה בעיה עם השרת"
                    return@addSnapshotListener
                }
                if (value == null)
                    return@addSnapshotListener
                liveData.value = value.documents.filter { it.data != null }.associate { it.id to Game.PlayerData.fromMap(it.data!!) }
            }
        allLiveData.add(liveData)
        listeners.add(listener)
        return liveData
    }

    suspend fun playersOnce(players : List<String>) : Map<String, Game.PlayerData> =
        GameActivity.game.playersFsRef
            .whereIn(FieldPath.documentId(), players)
            .get()
            .await()
            .documents.filter { it.data != null }.associate { it.id to Game.PlayerData.fromMap(it.data!!) }

    fun allPlayers() : LiveData<Map<String, Game.PlayerData>> {
        val liveData = MutableLiveData<Map<String, Game.PlayerData>>()
        val listener = GameActivity.game.playersFsRef
            .addSnapshotListener { value, error ->
                if (error != null) {
                    this.error.value = "מצטער אחי הייתה בעיה עם השרת"
                    return@addSnapshotListener
                }
                if (value == null)
                    return@addSnapshotListener
                liveData.value = value.documents.filter { it.data != null }.associate { it.id to Game.PlayerData.fromMap(it.data!!) }
            }
        allLiveData.add(liveData)
        listeners.add(listener)
        return liveData
    }

    suspend fun allPlayersOnce() : Map<String, Game.PlayerData> =
        GameActivity.game.playersFsRef
            .get()
            .await()
            .documents.filter { it.data != null }.associate { it.id to Game.PlayerData.fromMap(it.data!!) }

    override fun onCleared() {
        listeners.forEach { it.remove() }
        super.onCleared()
    }
}