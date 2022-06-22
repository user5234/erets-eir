@file:Suppress("UNCHECKED_CAST")

package game.eretseir

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DatabaseReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlin.math.max

val games = Firebase.firestore.collection("games")

val onlineReference = realtimeDatabase.getReference(".info/connected")

private val solutions = Firebase.firestore.collection("solutions")

/**
 * contains methods for game creating, updating and reading
 */
class Game private constructor(gameCode: String) {

    companion object {
        /**
         * returns a game object without firestore data, so it can be used synchronously
         *
         * call [bringUpToDate] to be able to use [snapshot] or [data]
         */
        fun fromExistingGame(gameCode: String): Game {
            return Game(gameCode)
        }

        /**
         * creates a game (in both RT DB and firestore) and returns a game object with data
         *
         * check connection to RT DB before calling this method
         */
        suspend fun createGame(gameCode: String, letter: String, maxPlayers: Long, rounds: Long, admin: String): Game {
            return Game(gameCode).apply { createGame(false, letter, maxPlayers, rounds, admin) }
        }

        /**
         * checks weather the game with this gameCode exists
         */
        suspend fun exists(gameCode: String): Boolean {
            return games.document(gameCode).get().await().exists()
        }
    }

    private val fsRef = games.document(gameCode)

    private val rtRef = realtimeDatabase.getReference("/online/$gameCode")

    val playersFsRef = fsRef.collection("players")

    /**
     * the [DocumentSnapshot] of this game
     *
     * don't use this variable until calling [bringUpToDate]
     */
    var snapshot: DocumentSnapshot? = null
        private set
        get() {
            if (field == null) error("didn't call bringUpToDate()")
            return field
        }

    /**
     * the [GameData] of this game
     *
     * don't use this variable until calling [bringUpToDate]
     */
    var data: GameData? = null
        private set
        get() {
            if (field == null) error("didn't call bringUpToDate()")
            return field
        }

    /**
     * only call this method if you are sure the game exists
     */
    suspend fun bringUpToDate() {
        snapshot = fsRef.get().await()
        if (!snapshot!!.exists())
            error("game doesn't exist")
        data = GameData.fromMap(snapshot?.data!!)
    }

    /**
     * creates a game document with the provided data
     *
     * does not check weather a game with the provided game code exists.
     * if one does exist it will override it
     */
    private suspend fun createGame(
        isRunning: Boolean,
        letter: String,
        maxPlayers: Long,
        rounds: Long,
        admin: String
    ): Unit = coroutineScope {

        val gameData = GameData(isRunning, letter, maxPlayers, rounds, admin).toMap()

        val userData = PlayerData().toMap()

        listOf(
            //creating the game in firestore
            async {
                fsRef.set(gameData).await()
                getPlayerFSRef(admin).set(userData).await()
            },
            //creating the game in RT DB
            async {
                getPlayerRTRef(admin).apply {
                    onDisconnect().removeValue().await()
                    setValue(true).await()
                }
            }
        ).awaitAll()
    }

    /**
     * updates the isRunning field to true
     */
    fun startGame(letter: String): Task<Void> {
        return fsRef.update(
            mapOf(
                "isRunning" to true,
                "letter" to letter
            )
        )
    }

    fun endGame(): Task<Void> {
        return fsRef.update("isRunning", false)
    }

    /**
     * add a snapshot listener with a [GameData] object for easier use
     */
    fun addSnapshotListener(action: (snapshot: DocumentSnapshot?, data: GameData?, error: FirebaseFirestoreException?) -> Unit) =
        fsRef.addSnapshotListener { snapshot, error ->
            action(snapshot, snapshot?.data?.let { GameData.fromMap(it) }, error)
        }

    /**
     * gets the names of all the players in the game from RT DB
     *
     * don't abuse this method as it requires reading from RT DB which costs money
     *
     * check connection to RT DB before calling this method
     */
    suspend fun getPlayersNames(): List<String> {
        return playersFsRef.get().await().documents.mapNotNull { it.id }
    }

    /**
     * get the DatabaseReference of this player in RT DB
     */
    private fun getPlayerRTRef(userName: String): DatabaseReference {
        return rtRef.child(userName)
    }

    /**
     * get the DatabaseReference of this player in RT DB
     */
    private fun getPlayerFSRef(userName: String): DocumentReference {
        return playersFsRef.document(userName)
    }

    /**
     * adds the player to both firestore and RT DB
     *
     * only the player joining thr game should call this method to add himself
     *
     * returns a list of Deferred - use [awaitAll()] to wait until the player is added
     *
     * check connection to RT DB before calling this method
     */
    suspend fun addPlayer(userName: String) = coroutineScope {

        listOf(
            //adding the player in firestore
            async {
                getPlayerFSRef(userName).set(PlayerData().toMap())
            },
            async {
                getPlayerRTRef(userName).apply {
                    onDisconnect().removeValue().await()
                    setValue(true).await()
                }
            }
        ).awaitAll()
    }

    /**
     * removes the player from the RT DB and then from firestore via the userDisconnected cloud function
     */
    fun removePlayer(userName: String) {
        getPlayerRTRef(userName).removeValue()
    }

    /**
     * gets all the solutions for the specified letter as a SolutionsData instance
     */
    suspend fun getSolutions(letter: String): SolutionsData {
        return SolutionsData.fromMap(solutions.document(letter).get().await().data as Map<String, List<String>>)
    }

    fun sendSolutions(userName: String, solutionsData: SolutionsData): Task<Void> {
        return getPlayerFSRef(userName).update(mutableMapOf<String, Any>("submittedAnswers" to true).apply { putAll(solutionsData.toMap()) })
    }

    suspend fun calculatePoints() = coroutineScope {

        /**
         * function to calculate how many points the player gets in a specific category
         */
        fun pointsInCategory(category : Map.Entry<String, List<String>>, otherSolutions : Collection<Map<String, List<String>>>) : Int {
            var points = 0
            val categoryName = category.key
            val categorySolutions = category.value
            //iterating over each solution of the category
            categorySolutions.forEach { solution ->
                //another player submitted the same answer in this category
                if (otherSolutions.any { otherPlayerSolutions -> otherPlayerSolutions[categoryName]!!.contains(solution) }) {
                    points = max(points, 5)
                    return@forEach
                }
                //another player submitted another solution in the same category
                if (otherSolutions.any { otherPlayerSolutions -> otherPlayerSolutions[categoryName]!!.isNotEmpty() }) {
                    points = max(points, 10)
                    return@forEach
                }
                //no other player submitted any answer in this category
                return 15
            }
            return points
        }

        //start of calculation
        val excludedData = PlayerData().toMap()
        //map of { each player's document reference : map of { category name : list of solutions } }
        val allSubmittedSolutions : Map<DocumentReference, Map<String, List<String>>> =
            playersFsRef.get().await()
                .documents
                .filter {
                    val submittedAnswers = it.data?.get("submittedAnswers") == true
                    if (!submittedAnswers) removePlayer(it.id)
                    submittedAnswers
                }
                .associate {
                    it.reference to it.data!!.filterKeys { key -> key !in excludedData } as Map<String, List<String>>
                }
        //used for writing the data
        val batch = Firebase.firestore.batch()
        //iterating over each player
        allSubmittedSolutions.forEach players@ { player ->
            var points = 0L
            val otherSolutions = allSubmittedSolutions.filter { it != player }.values
            //iterating over each category of the solutions the player submitted
            player.value.entries.forEach { category -> points += pointsInCategory(category, otherSolutions) }
            batch.update(player.key,
                mapOf(
                    "points" to FieldValue.increment(points),
                    "submittedAnswers" to false
                )
            )
        }
        batch.commit()
    }

    /**
     * structured way of retrieving and setting game data
     */
    data class GameData(
        var isRunning: Boolean,
        var letter: String,
        var maxPlayers: Long,
        val rounds: Long,
        var admin: String
    ) {

        companion object {
            internal fun fromMap(data: Map<String, Any>): GameData {
                return GameData(
                    data["isRunning"] as Boolean,
                    data["letter"] as String,
                    data["maxPlayers"] as Long,
                    data["rounds"] as Long,
                    data["admin"] as String,
                )
            }
        }

        internal fun toMap(): Map<String, Any> {
            return mapOf(
                "isRunning" to isRunning,
                "letter" to letter,
                "maxPlayers" to maxPlayers,
                "rounds" to rounds,
                "admin" to admin
            )
        }
    }

    /**
     * structured way of retrieving and setting player data
     */
    data class PlayerData(
        var points: Long = 0,
        var submittedAnswers: Boolean = false
    ) {

        companion object {
            internal fun fromMap(data: Map<String, Any>): PlayerData {
                return PlayerData().apply {
                    points = data["points"] as Long
                    submittedAnswers = data["submittedAnswers"] as Boolean
                }
            }
        }

        fun toMap(): Map<String, Any> {
            return mapOf(
                "points" to points,
                "submittedAnswers" to submittedAnswers
            )
        }
    }

    /**
     * structured way of retrieving and setting solutions
     */
    data class SolutionsData(
        var erets: MutableList<String> = mutableListOf(),
        var eir: MutableList<String> = mutableListOf(),
        var hai: MutableList<String> = mutableListOf(),
        var tsomeah: MutableList<String> = mutableListOf(),
        var domem: MutableList<String> = mutableListOf(),
        var miktsoa: MutableList<String> = mutableListOf(),
        var yeled: MutableList<String> = mutableListOf(),
        var yalda: MutableList<String> = mutableListOf()
    ) {

        companion object {
            internal fun fromMap(data: Map<String, List<String>>): SolutionsData {
                return SolutionsData().apply {
                    erets = data["ארץ"]!!.toMutableList()
                    eir = data["עיר"]!!.toMutableList()
                    hai = data["חי"]!!.toMutableList()
                    tsomeah = data["צומח"]!!.toMutableList()
                    domem = data["דומם"]!!.toMutableList()
                    miktsoa = data["מקצוע"]!!.toMutableList()
                    yeled = data["ילד"]!!.toMutableList()
                    yalda = data["ילדה"]!!.toMutableList()
                }
            }
        }

        fun toMap(): Map<String, MutableList<String>> {
            return mapOf(
                "erets" to erets,            //ארץ
                "eir" to eir,                //עיר
                "hai" to hai,                //חי
                "tsomeah" to tsomeah,        //צומח
                "domem" to domem,            //דומם
                "miktsoa" to miktsoa,        //מקצוע
                "yeled" to yeled,            //ילד
                "yalda" to yalda,            //ילדה
            )
        }

        fun toMapHebrew(): Map<String, MutableList<String>> {
            return mapOf(
                "ארץ" to erets,              //ארץ
                "עיר" to eir,                //עיר
                "חי" to hai,                 //חי
                "צומח" to tsomeah,           //צומח
                "דומם" to domem,             //דומם
                "מקצוע" to miktsoa,          //מקצוע
                "ילד" to yeled,              //ילד
                "ילדה" to yalda              //ילדה
            )
        }
    }
}