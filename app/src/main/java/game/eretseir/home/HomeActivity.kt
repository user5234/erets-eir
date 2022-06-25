package game.eretseir.home

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import gal.libs.fullscreenactivity.FullScreenActivity
import game.eretseir.databinding.HomeActivityBinding
import game.eretseir.online.OnlineActivity

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
        val auth = FirebaseAuth.getInstance()
        auth.currentUser ?: auth.signInAnonymously()

        val realtimeDatabase = FirebaseDatabase.getInstance()

        realtimeDatabase.getReference(".info/connected").addValueEventListener(object : ValueEventListener {

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