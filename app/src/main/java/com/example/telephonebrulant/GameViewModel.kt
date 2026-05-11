package com.example.telephonebrulant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameEvent {
    NONE,
    COOLING_BOOST,  //bonusde refroidisseement = secosses plus efficace
    OVERCLOCK,      // malus =chauffe x2
    SENSOR_CRAZY,   // malus =capteur moins précis
    INSTABILITY ,    // bugs plus fréquents
    AXIS_X, //horizontalement
    AXIS_Y,//verticalement
}

data class GameState(
    val heat: Float = 0f, //si 100, perdu
    val isRunning: Boolean = false,
    val isGameOver: Boolean = false,
    val isBugMode: Boolean = false,
    val currentEvent: GameEvent = GameEvent.NONE, //l'évènement spécial en cours
    val score: Int = 0
)

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    fun startGame() {
        _state.value = GameState(isRunning = true)
        startHeatLoop()
        startEventLoop()
        startBugModeLoop()
    }

    //fun onShake(intensity: Float) { //appeller à chaque secousse
    fun onShake(x : Float, y : Float, z : Float){
        val s = _state.value
        if (!s.isRunning) return

        val isCorrectAxis=when (s.currentEvent){
            GameEvent.AX
        }
        //val effectiveIntensity = if (s.currentEvent == GameEvent.SENSOR_CRAZY) //Si SENSOR_CRAZY on divise l'intensité par 2.5
            //intensity * 0.4f else intensity
        val intensity = sqrt(x * x + y * y + z * z)

        val coolingMultiplier = if (s.currentEvent == GameEvent.COOLING_BOOST) 2f else 1f //Si COOLING_BOOST,2f , augmentation de l'intensité

        val temperatureChange = effectiveIntensity * coolingMultiplier * 0.3f

        val newHeat = if (s.isBugMode) //si mode bugé, secouer le téléphone augmente la chaleur au lieu de la baisser
            (s.heat + temperatureChange).coerceIn(0f, 100f)
        else
            (s.heat - temperatureChange).coerceIn(0f, 100f)

        _state.value = s.copy(heat = newHeat)
    }

    private fun startHeatLoop() { //boucle chaleur
        viewModelScope.launch {
            while (_state.value.isRunning) {
                delay(100)
                val s = _state.value
                if (!s.isRunning) break
                val baseIncrease = 0.3f
                val increase = if (s.currentEvent == GameEvent.OVERCLOCK)
                    baseIncrease * 2f else baseIncrease
                val newHeat = (s.heat + increase).coerceIn(0f, 100f)

                // Score ,plus de points dans les zones chaudes
                val points = when {
                    s.heat > 80f -> 4
                    s.heat > 60f -> 2
                    else         -> 1
                }

                if (newHeat >= 100f) {
                    _state.value = s.copy(
                        heat = 100f,
                        isRunning = false,
                        isGameOver = true
                    )
                } else {
                    _state.value = s.copy(
                        heat = newHeat,
                        score = s.score + points
                    )
                }
            }
        }
    }

    private fun startEventLoop() { //boucle qui gère les évènemen,ts
        viewModelScope.launch {
            while (_state.value.isRunning) {
                delay(Random.nextLong(5000, 10000))//wait en,tre 5000 et 10000 avant de lancer un event
                if (!_state.value.isRunning) break
                val event = listOf(
                    GameEvent.COOLING_BOOST,
                    GameEvent.OVERCLOCK,
                    GameEvent.SENSOR_CRAZY,
                    GameEvent.INSTABILITY,
                    GameEvent.NONE
                ).random()
                _state.value = _state.value.copy(currentEvent = event)
                delay(4000)
                _state.value = _state.value.copy(currentEvent = GameEvent.NONE)
            }
        }
    }

    private fun startBugModeLoop() {
        viewModelScope.launch {
            while (_state.value.isRunning) {
                val baseDelay = if (_state.value.currentEvent == GameEvent.INSTABILITY)
                    Random.nextLong(3000, 6000)//soit très vite entre 3 et 6 sec
                else
                    Random.nextLong(8000, 15000)//ou entre 8 et 15sec

                delay(baseDelay)
                if (!_state.value.isRunning) break
                _state.value = _state.value.copy(isBugMode = true) //active le mode bugé
                delay(Random.nextLong(2000, 4000))//le bug dure entre 2 et 4 sec
                _state.value = _state.value.copy(isBugMode = false)
            }
        }
    }

    fun resetGame() {
        _state.value = GameState()
    }
}