package com.example.telephonebrulant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

/**/
class MainActivity : ComponentActivity() {
    private lateinit var shakeDetector: ShakeDetector
    private val viewModel : GameViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
      //  setContentView(R.layout.)
        shakeDetector= ShakeDetector(this){
            intensity -> viewModel.onShake(intensity)
        }
      //  enableEdgeToEdge()
        setContent {
            GameScreen(viewModel=viewModel)
        }
    }
    override fun onResume() {
        super.onResume()
        shakeDetector.start()
    }

    override fun onPause() {
        super.onPause()
        shakeDetector.stop()
    }
}
