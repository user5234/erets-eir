package game.eretseir.online

import android.os.Bundle
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import game.eretseir.FullScreenActivity
import game.eretseir.R
import game.eretseir.databinding.OnlineActivityBinding


class OnlineActivity : FullScreenActivity() {

    companion object {
        lateinit var instance : OnlineActivity; private set
        lateinit var scope : LifecycleCoroutineScope
    }

    lateinit var binding : OnlineActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        instance = this
        scope = lifecycleScope

        binding = OnlineActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //adding and animating the create new game screen
        binding.createGameButton.setOnClickListener {
            val v = layoutInflater.inflate(R.layout.create_new_game, binding.root, false)
            val anim = AnimationUtils.loadAnimation(this, R.anim.view_popup)
            (binding.root as ViewGroup).addView(v).also { v.startAnimation(anim) }
            binding.homeButton.isClickable = false
        }

        binding.joinGameButton.setOnClickListener {
            val v = layoutInflater.inflate(R.layout.join_game, binding.root as ViewGroup, false)
            val anim = AnimationUtils.loadAnimation(this, R.anim.view_popup)
            (binding.root as ViewGroup).addView(v).also { v.startAnimation(anim) }
            binding.homeButton.isClickable = false
        }

        //going to home activity
        binding.homeButton.setOnClickListener { onBackPressed() }
    }
}