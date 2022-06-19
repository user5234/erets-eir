package game.eretseir.game.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.HorizontalScrollView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_FADE
import androidx.fragment.app.viewModels
import game.eretseir.*
import game.eretseir.databinding.GameFragmentBinding
import game.eretseir.databinding.GameSolutionsRowBinding
import game.eretseir.game.activities.GameActivity
import game.eretseir.game.adapters.GameSolutionsRecyclerAdapter
import game.eretseir.game.viewmodels.GameViewModel
import kotlin.math.max


class GameFragment : Fragment() {

    private val viewModel : GameViewModel by viewModels()

    private lateinit var gameActivity : GameActivity
    private lateinit var binding : GameFragmentBinding

    override fun onAttach(context: Context) {
        super.onAttach(context)
        gameActivity = context as GameActivity
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = GameFragmentBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.entryScreenLetter.text = getString(R.string.gameLetter, GameActivity.letter)
        binding.infoLetter.text = GameActivity.letter

        viewModel.allSolutions().observe(viewLifecycleOwner) { allSolutions -> //map of categories: category name to its solutions
            //Creating the rows for the game
            //used for the layout params
            var prevId: Int? = null
            //iterating over the player solutions (The map that holds the data the player will submit)
            viewModel.playerSolutions.toMapHebrew().entries.forEach { category -> //key is the name, value is the solutions
                GameSolutionsRowBinding.inflate(layoutInflater, binding.gameSolutions, true).apply {
                    recyclerView.adapter = GameSolutionsRecyclerAdapter(allSolutions[category.key]!!, category.value) // category.value will change as the user submits his answers, so we use it later to send the solutions to tha database
                    textView.text = category.key
                    val params = root.layoutParams as ConstraintLayout.LayoutParams
                    params.width = max(spToPx(170F, gameActivity), binding.scrollView.width / 8F).toInt()
                    if (prevId != null)
                        params.startToEnd = prevId!!
                    else
                        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    root.requestLayout()
                    root.id = View.generateViewId()
                    prevId = root.id
                }
            }
            binding.scrollView.doOnLayout { (it as HorizontalScrollView).fullScroll(View.FOCUS_RIGHT) }
        }

        viewModel.secondsLeft().observe(viewLifecycleOwner) { secondsLeft ->
            val countDownAnim = AnimationUtils.loadAnimation(gameActivity, R.anim.countdown)
            //update the timers
            binding.infoTimer.text = "$secondsLeft"
            if (secondsLeft > viewModel.playtime) {
                binding.entryScreenCountdown.text = if (secondsLeft == viewModel.playtime + 1) "GO" else "${secondsLeft - viewModel.playtime - 1}"
                binding.entryScreenCountdown.startAnimation(countDownAnim)
            }
            //remove the countdown screen
            if (secondsLeft == viewModel.playtime)
                binding.root.removeWithAnimation(binding.entryScreen)
            //the game ended
            if (secondsLeft == 0) {
                binding.root.let { it.addWithAnimation(layoutInflater.inflate(R.layout.game_end_screen, it, false)) }
                gameActivity.closeKeyboard()
                view.post { binding.root.requestFocus() }
                return@observe
            }
            //the solutions were sent so go back to lobby or go to the final scores fragment
            if (secondsLeft != -3)
                return@observe
            //there are still some rounds left
            if (GameActivity.rounds != 1) {
                parentFragmentManager.beginTransaction().setTransition(TRANSIT_FRAGMENT_FADE).replace(R.id.fragmentContainer, LobbyFragment()).commit()
                return@observe
            }
            //this was the last round
            parentFragmentManager.beginTransaction().setTransition(TRANSIT_FRAGMENT_FADE).replace(R.id.fragmentContainer, FinalScoresFragment()).commit()
        }

        viewModel.adminLeft().observe(viewLifecycleOwner) { left -> if (left) somethingBadHappened("מצטער אחי המלך עזב") }

        viewModel.error().observe(viewLifecycleOwner) { error -> somethingBadHappened(error) }
    }

    private fun somethingBadHappened(description: String) {
        //stop listening to the data from the view model
        viewModel.allLiveData().forEach { liveData -> liveData.removeObservers(viewLifecycleOwner) }
        //show an alert with the description
        showAlert(layoutInflater, binding.root, true, description, "חזור") { gameActivity.onBackPressed() }
    }
}