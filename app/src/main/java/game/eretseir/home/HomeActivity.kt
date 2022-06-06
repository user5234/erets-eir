package game.eretseir.home

import android.content.Intent
import android.os.Bundle
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import gal.libs.fullscreenactivity.FullScreenActivity
import game.eretseir.auth
import game.eretseir.databinding.HomeActivityBinding
import game.eretseir.online.OnlineActivity
import game.eretseir.onlineReference
import game.eretseir.realtimeDatabase

/**
 * used to check connection to RT DB
 */
var connectedToRTDB = false

private val disconnectListeners : MutableMap<() -> Unit, Boolean> = mutableMapOf()
private val connectListeners : MutableMap<() -> Unit, Boolean> = mutableMapOf()
private val connectionChangeListeners : MutableMap<(Boolean) -> Unit, Boolean> = mutableMapOf()

fun addOnDisconnectListener(runOnce : Boolean = false, action : () -> Unit) { disconnectListeners[action] = runOnce }
fun addOnConnectListener(runOnce : Boolean = false, action : () -> Unit) { connectListeners[action] = runOnce }
fun addConnectionChangeListener(runOnce : Boolean = false, action: (Boolean) -> Unit) { connectionChangeListeners[action] = runOnce }

fun removeOnDisconnectListener(action: () -> Unit) = disconnectListeners.remove(action)
fun removeOnConnectListener(action: () -> Unit) = connectListeners.remove(action)
fun removeConnectionChangeListener(action: (Boolean) -> Unit) = connectionChangeListeners.remove(action)

class HomeActivity : FullScreenActivity() {

    companion object {
        lateinit var instance : HomeActivity; private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        instance = this

        val binding = HomeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //important stuff to do when the user connects to the app
        auth.currentUser ?: auth.signInAnonymously()

        onlineReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                //connected or not
                connectedToRTDB = snapshot.getValue(Boolean::class.java)!!
                //fire off listeners and remove all one time listeners
                connectionChangeListeners.keys.forEach { it(connectedToRTDB) }
                connectionChangeListeners.values.removeAll { it }
                //connected
                if (connectedToRTDB) {
                    connectListeners.keys.forEach { it() }
                    connectListeners.values.removeAll { it }
                }
                //disconnected
                else {
                    disconnectListeners.keys.forEach { it() }
                    disconnectListeners.values.removeAll { it }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                connectedToRTDB = false
                //fire off listeners and remove all one time listeners
                connectionChangeListeners.keys.forEach { it(connectedToRTDB) }
                connectionChangeListeners.values.removeAll { it }
                //disconnect listeners
                disconnectListeners.keys.forEach { it() }
                disconnectListeners.values.removeAll { it }
            }
        })

        //this is to keep the connection active
        realtimeDatabase.getReference("/random").keepSynced(true)

        //going to online activity
        binding.onlineButton.setOnClickListener { startActivity(Intent(this, OnlineActivity::class.java)) }
    }
}