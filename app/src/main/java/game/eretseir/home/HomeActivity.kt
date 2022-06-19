package game.eretseir.home

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
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
var connectedToRTDB = MutableLiveData(false)

class HomeActivity : FullScreenActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = HomeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //important stuff to do when the user connects to the app
        auth.currentUser ?: auth.signInAnonymously()

        onlineReference.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                connectedToRTDB.value = snapshot.getValue(Boolean::class.java)!!
            }

            override fun onCancelled(error: DatabaseError) {
                connectedToRTDB.value = false
            }
        })

        //this is to keep the connection active
        realtimeDatabase.getReference("/random").keepSynced(true)

        //going to online activity
        binding.onlineButton.setOnClickListener { startActivity(Intent(this, OnlineActivity::class.java)) }
    }
}