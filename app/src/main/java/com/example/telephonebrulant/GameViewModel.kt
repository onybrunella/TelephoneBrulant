package com.example.telephonebrulant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.random.Random

enum class PowerUpType{
    ICE_PACK, //-20 degrès immédiatement
    LIQUID_NITROGEN, //bloque température pdt 5s
    ANTIVIRUS //supprime l'event courant ou le mode bugé
}

data class PowerUp(
    val type: PowerUpType,
    val xPercent:Float, //position X en % de l'écran
    val yPercent:Float,//position Y en % de l'écran
    )

enum class RequiredAxis{ANY,X,Y}

enum class GameEvent {
    NONE,
    COOLING_BOOST,  //secousses plus efficaces
    OVERCLOCK,      //chauffe x2
    SENSOR_CRAZY,   //capteur moins précis
    INSTABILITY,    //pics de chaleur
}

data class GameState(
    val heat: Float = 0f,
    val isRunning: Boolean = false,
    val isGameOver: Boolean = false,
    val isBugMode: Boolean = false,
    val currentEvent: GameEvent = GameEvent.NONE,
    val score: Int = 0,
    val isHeatModeEnabled: Boolean = false,
    val activePowerUp: PowerUp?=null,
    val isNitrogenActive:Boolean=false,
    val isEventActive: Boolean=false,
    val requiredAxis: RequiredAxis= RequiredAxis.ANY,
    val nitrogenSeconds: Int = 0

)

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    // Compte le nombre de ticks (1 tick = 100ms)
    private var survivalTicks = 0

    fun startGame(heatModeEnabled: Boolean = false) {
        survivalTicks = 0
        _state.value = GameState(
            isRunning = true,
            isHeatModeEnabled = heatModeEnabled
        )
        startHeatLoop()
    //    startEventLoop()
        startEventAndPowerUpLoop()
        startBugModeLoop()
        startAxisLoop()
        if (heatModeEnabled) startHeatMode()
    }

    fun onShake(x: Float, y: Float, z: Float) {
        val s = _state.value
        if (!s.isRunning) return

        val intensity = sqrt(x * x + y * y + z * z)

        val effectiveIntensity = when {
            !isCorrectAxis(s, x, y, z)               -> 0f
            s.currentEvent == GameEvent.SENSOR_CRAZY  -> intensity * 0.4f
            else                                      -> intensity
        }

        val coolingMultiplier = if (s.currentEvent == GameEvent.COOLING_BOOST) 2f else 1f

        // Le refroidissement max diminue avec le temps — min 0.3°
        val maxCooling = (0.8f - (survivalTicks / 100) * 0.08f).coerceAtLeast(0.15f)
        val delta = (effectiveIntensity * coolingMultiplier * 0.15f).coerceAtMost(maxCooling)

        // Le plancher de chaleur monte avec le temps — impossible de retomber à 0°
        val heatFloor = (survivalTicks / 100) * 2f

        val newHeat = if (s.isBugMode)
            (s.heat + delta).coerceIn(0f, 100f)
        else
            (s.heat - delta).coerceIn(heatFloor, 100f)

        _state.value = s.copy(heat = newHeat)
    }

    private fun startAxisLoop() {
        viewModelScope.launch {
            while (_state.value.isRunning) {
                delay(Random.nextLong(15000, 20000))
                if (!_state.value.isRunning) break

                val newAxis = listOf(
                    RequiredAxis.X,
                    RequiredAxis.Y,
                    RequiredAxis.ANY
                ).random()

                _state.value = _state.value.copy(requiredAxis = newAxis)
            }
        }
    }

    private fun isCorrectAxis(s: GameState, x: Float, y: Float, z: Float): Boolean {
        return when (s.requiredAxis) {
            RequiredAxis.ANY -> true
            RequiredAxis.X   -> x > y && x > z
            RequiredAxis.Y   -> y > x && y > z
        }
    }

    private fun startHeatLoop() {
        viewModelScope.launch {
            while (_state.value.isRunning) {
                delay(100)
                val s = _state.value
                if (!s.isRunning) break

                survivalTicks++

                // Difficulté croissante : +10% de vitesse toutes les 10 secondes
                val difficultyMultiplier = 1f + (survivalTicks / 100) * 0.1f

                //val baseIncrease = 0.3f * difficultyMultiplier
                val baseIncrease = if (_state.value.isNitrogenActive) 0f
                else 0.3f * difficultyMultiplier

                val increase = if (s.currentEvent == GameEvent.OVERCLOCK)
                    baseIncrease * 2f else baseIncrease

                val newHeat = (s.heat + increase).coerceIn(0f, 100f)

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

//    private fun startEventLoop() {
//        viewModelScope.launch {
//            while (_state.value.isRunning) {
//                delay(Random.nextLong(5000, 10000))
//                if (!_state.value.isRunning) break
//                while(_state.value.activePowerUp!=null)delay(500)
//
//                val event = listOf(
//                    GameEvent.COOLING_BOOST,
//                    GameEvent.OVERCLOCK,
//                    GameEvent.SENSOR_CRAZY,
//                    GameEvent.INSTABILITY,
//                    GameEvent.AXIS_X,
//                    GameEvent.AXIS_Y,
//                    GameEvent.NONE
//                ).random()
//
//                _state.value = _state.value.copy(currentEvent = event)
//
//                // INSTABILITY envoie 4 pics de chaleur espacés d'une seconde
//                if (event == GameEvent.INSTABILITY) {
//                    repeat(4) {
//                        delay(1000)
//                        if (_state.value.isRunning) {
//                            _state.value = _state.value.copy(
//                                heat = (_state.value.heat + 8f).coerceAtMost(100f)
//                            )
//                        }
//                    }
//                } else {
//                    delay(4000)
//                }
//
//                _state.value = _state.value.copy(currentEvent = GameEvent.NONE)
//            }
//        }
//    }

    private fun startBugModeLoop() {
        viewModelScope.launch {
            while (_state.value.isRunning) {
                val baseDelay = if (_state.value.currentEvent == GameEvent.INSTABILITY)
                    Random.nextLong(3000, 6000)
                else
                    Random.nextLong(8000, 15000)

                delay(baseDelay)
                if (!_state.value.isRunning) break

                _state.value = _state.value.copy(isBugMode = true)

                delay(Random.nextLong(2000, 4000))
                _state.value = _state.value.copy(isBugMode = false)
            }
        }
    }

    private fun startHeatMode() {
        viewModelScope.launch(Dispatchers.Default) {
            while (_state.value.isRunning) {
                var pi = 0.0
                var sign = 1.0
                for (i in 0..1_000_000) {
                    val denominator = (2 * i + 1).toDouble()
                    pi += sign * (4.0 / denominator)
                    sign = -sign
                }
                delay(100)
            }
        }
    }

//    private fun startPowerUpLoop(){
//        viewModelScope.launch {
//            while(_state.value.isRunning){
//                delay(Random.nextLong(10000, 18000))
//                if(!_state.value.isRunning)break
//                while(_state.value.currentEvent!= GameEvent.NONE)delay(500)
//                val powerUp= PowerUp(
//                    type= PowerUpType.entries.random(),
//                    xPercent = Random.nextFloat() * 0.75f,
//                    yPercent = Random.nextFloat() * 0.75f
//                )
//                _state.value=_state.value.copy(activePowerUp = powerUp)
//                delay(3000)
//                _state.value=_state.value.copy(activePowerUp = null)
//            }
//        }
//    }
private fun startEventAndPowerUpLoop() {
    viewModelScope.launch {
        while (_state.value.isRunning) {
            delay(Random.nextLong(5000, 10000))
            if (!_state.value.isRunning) break

            // On vérifie qu'aucun event n'est déjà actif
            if (_state.value.isEventActive) continue

            // On pose le verrou immédiatement — une seule coroutine peut passer
            _state.value = _state.value.copy(isEventActive = true)

            // On choisit aléatoirement : event ou power-up
            if (Random.nextBoolean()) {
                spawnEvent()
            } else {
                spawnPowerUp()
            }

            // On libère le verrou
            _state.value = _state.value.copy(isEventActive = false)
        }
    }
}

    private suspend fun spawnEvent() {
        val event = listOf(
            GameEvent.COOLING_BOOST,
            GameEvent.OVERCLOCK,
            GameEvent.SENSOR_CRAZY,
            GameEvent.INSTABILITY,
            GameEvent.NONE
        ).random()

        _state.value = _state.value.copy(currentEvent = event)

        if (event == GameEvent.INSTABILITY) {
            repeat(4) {
                delay(1000)
                if (_state.value.isRunning) {
                    _state.value = _state.value.copy(
                        heat = (_state.value.heat + 8f).coerceAtMost(100f)
                    )
                }
            }
        } else {
            delay(4000)
        }

        _state.value = _state.value.copy(currentEvent = GameEvent.NONE)
    }

    private suspend fun spawnPowerUp() {
        val powerUp = PowerUp(
            type = PowerUpType.entries.random(),
            xPercent = Random.nextFloat() * 0.75f,
            yPercent = Random.nextFloat() * 0.75f
        )

        _state.value = _state.value.copy(activePowerUp = powerUp)

        delay(3000)

        // Expire si pas cliqué
        _state.value = _state.value.copy(activePowerUp = null)
    }

    fun onPowerUpCollected() {
        val s = _state.value
        val powerUp = s.activePowerUp ?: return

        when (powerUp.type) {
            PowerUpType.ICE_PACK -> {
                _state.value = s.copy(
                    heat = (s.heat - 20f).coerceAtLeast(0f),
                    activePowerUp = null
                )
            }
            PowerUpType.LIQUID_NITROGEN -> {
                _state.value = s.copy(
                    activePowerUp = null,
                    isNitrogenActive = true,
                    nitrogenSeconds = 5
                )
                viewModelScope.launch {
                    repeat(5) {
                        delay(1000)
                        _state.value = _state.value.copy(
                            nitrogenSeconds = _state.value.nitrogenSeconds-1
                        )
                    }
                    _state.value = _state.value.copy(isNitrogenActive = false, nitrogenSeconds = 0)
                }
            }
            PowerUpType.ANTIVIRUS -> {
                _state.value = s.copy(
                    activePowerUp = null,
                    isBugMode = false,
                    currentEvent = GameEvent.NONE
                )
            }
        }
    }
    fun resetGame() {
        _state.value = GameState()
    }
}