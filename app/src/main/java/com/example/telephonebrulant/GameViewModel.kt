package com.example.telephonebrulant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

enum class PowerUpType {
    ICE_PACK, // -20 degrés immédiatement
    LIQUID_NITROGEN, // bloque température pdt 5s
    ANTIVIRUS // supprime l'event courant ou le mode bugé
}

data class PowerUp(
    val type: PowerUpType,
    val xPercent: Float, // position X en % de l'écran
    val yPercent: Float, // position Y en % de l'écran
)

enum class RequiredAxis { ANY, X, Y }

enum class GameEvent {
    NONE,
    COOLING_BOOST,  // secousses plus efficaces
    OVERCLOCK,      // chauffe x2
    SENSOR_CRAZY,   // capteur moins précis
    INSTABILITY,    // pics de chaleur
}

data class GameState(
    val heat: Float = 0f,
    val isRunning: Boolean = false,
    val isGameOver: Boolean = false,
    val isBugMode: Boolean = false,
    val currentEvent: GameEvent = GameEvent.NONE,
    val score: Int = 0,
    val activePowerUp: PowerUp? = null,
    val isNitrogenActive: Boolean = false,
    val isEventActive: Boolean = false,
    val requiredAxis: RequiredAxis = RequiredAxis.ANY,
    val nitrogenSeconds: Int = 0
)

class GameViewModel : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state

    private var survivalTicks = 0
    private var heatJobs = mutableListOf<Job>()

    private var powerUpTimerJob: Job? = null
    private var nitrogenTimerJob: Job? = null
    private var currentEventJob: Job? = null // Job dédié pour pouvoir annuler l'event en cours

    fun startGame(heatModeEnabled: Boolean = false) {
        stopAllJobs()
        survivalTicks = 0
        _state.value = GameState(isRunning = true)

        startHeatLoop()
        startEventAndPowerUpLoop()
        startBugModeLoop()
        startAxisLoop()
        if (heatModeEnabled) startHeatMode()
    }

    fun onShake(x: Float, y: Float, z: Float) {
        val s = _state.value
        if (!s.isRunning) return

        val rawIntensity = sqrt(x * x + y * y + z * z)

        val axisOk = when (s.requiredAxis) {
            RequiredAxis.X   -> abs(x) > abs(y) && abs(x) > abs(z)
            RequiredAxis.Y   -> abs(y) > abs(x) && abs(y) > abs(z)
            RequiredAxis.ANY -> true
        }
        if (!axisOk) return

        val effectiveIntensity = if (s.currentEvent == GameEvent.SENSOR_CRAZY)
            rawIntensity * 0.4f else rawIntensity

        val coolingMultiplier = if (s.currentEvent == GameEvent.COOLING_BOOST) 2f else 1f

        val maxCooling = (0.8f - (survivalTicks / 100) * 0.08f).coerceAtLeast(0.15f)
        val delta = (effectiveIntensity * coolingMultiplier * 0.10f).coerceAtMost(maxCooling)

        val heatFloor = (survivalTicks / 100) * 2f

        val newHeat = if (s.isBugMode)
            (s.heat + delta).coerceIn(0f, 100f)
        else if (s.isNitrogenActive)
            s.heat
        else
            (s.heat - delta).coerceIn(heatFloor, 100f)

        _state.value = s.copy(heat = newHeat)
    }

    private fun startEventAndPowerUpLoop() {
        viewModelScope.launch {
            while (_state.value.isRunning) {
                delay(Random.nextLong(4000, 7000))
                if (!_state.value.isRunning) break

                val s = _state.value

                // Logique de décision intelligente :
                // 1. Si un événement est en cours ou qu'on est en Bug Mode, on force/favorise le spawn d'un Power-up (Antivirus)
                // 2. Sinon, on alterne entre Event et Power-up
                val shouldSpawnPowerUp = s.isEventActive || s.isBugMode || Random.nextFloat() < 0.4f

                if (shouldSpawnPowerUp) {
                    // On ne spawn pas de bonus si un autre est déjà à l'écran
                    if (s.activePowerUp == null) {
                        spawnPowerUp()
                    }
                } else if (!s.isEventActive) {
                    // On ne spawn un event que si le précédent est fini
                    currentEventJob = launch { spawnEvent() }
                }
            }
        }
    }

    private suspend fun spawnEvent() {
        val event = listOf(
            GameEvent.COOLING_BOOST,
            GameEvent.OVERCLOCK,
            GameEvent.SENSOR_CRAZY,
            GameEvent.INSTABILITY
        ).random()

        _state.value = _state.value.copy(currentEvent = event, isEventActive = true)

        try {
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
                delay(5000)
            }
        } finally {
            // S'assure que l'état revient à la normale même si le job est annulé (par l'antivirus)
            _state.value = _state.value.copy(currentEvent = GameEvent.NONE, isEventActive = false)
        }
    }

    private fun spawnPowerUp() {
        powerUpTimerJob?.cancel()

        val s = _state.value
        val problemActive = s.isEventActive || s.isBugMode

        // Antivirus seulement si un problème est actif
        val type = when {
            problemActive && Random.nextFloat() < 0.4f -> PowerUpType.ANTIVIRUS
            else -> listOf(
                PowerUpType.ICE_PACK,
                PowerUpType.LIQUID_NITROGEN
            ).random() // jamais d'antivirus si rien à annuler
        }

        val powerUp = PowerUp(
            type = type,
            xPercent = Random.nextFloat() * 0.75f,
            yPercent = Random.nextFloat() * 0.75f
        )

        _state.value = _state.value.copy(activePowerUp = powerUp)

        powerUpTimerJob = viewModelScope.launch {
            delay(3500)
            _state.value = _state.value.copy(activePowerUp = null)
        }
    }

    fun onPowerUpCollected() {
        val s = _state.value
        if (!s.isRunning) return
        val powerUp = s.activePowerUp ?: return

        powerUpTimerJob?.cancel()
        _state.value = s.copy(activePowerUp = null)

        when (powerUp.type) {
            PowerUpType.ICE_PACK -> {
                _state.value = _state.value.copy(heat = (_state.value.heat - 5f).coerceAtLeast(0f))
            }
            PowerUpType.LIQUID_NITROGEN -> {
                nitrogenTimerJob?.cancel()
                nitrogenTimerJob = viewModelScope.launch {
                    _state.value = _state.value.copy(isNitrogenActive = true, nitrogenSeconds = 5)
                    repeat(5) {
                        delay(1000)
                        _state.value = _state.value.copy(nitrogenSeconds = _state.value.nitrogenSeconds - 1)
                    }
                    _state.value = _state.value.copy(isNitrogenActive = false, nitrogenSeconds = 0)
                }
            }
            PowerUpType.ANTIVIRUS -> {
                currentEventJob?.cancel()
                _state.value = _state.value.copy(
                    isBugMode = false,
                    currentEvent = GameEvent.NONE,
                    isEventActive = false
                )
            }
        }
    }

    private fun startHeatLoop() {
        viewModelScope.launch {
            while (_state.value.isRunning) {
                delay(100)
                val s = _state.value
                if (!s.isRunning) break

                survivalTicks++
                val difficultyMultiplier = 1f + (survivalTicks / 100) * 0.1f
                val baseIncrease = if (s.isNitrogenActive) 0f else 0.3f * difficultyMultiplier
                val increase = if (s.currentEvent == GameEvent.OVERCLOCK) baseIncrease * 2f else baseIncrease

                val newHeat = (s.heat + increase).coerceIn(0f, 100f)
                val points = when {
                    s.heat > 80f -> 4
                    s.heat > 60f -> 2
                    else         -> 1
                }

                if (newHeat >= 100f) {
                    _state.value = s.copy(heat = 100f, isRunning = false, isGameOver = true)
                    stopAllJobs()
                } else {
                    _state.value = s.copy(heat = newHeat, score = s.score + points)
                }
            }
        }
    }

    private fun startBugModeLoop() {
        viewModelScope.launch {
            while (_state.value.isRunning) {
                delay(Random.nextLong(12000, 18000))
                if (!_state.value.isRunning || _state.value.isEventActive) continue

                _state.value = _state.value.copy(isBugMode = true)
                delay(Random.nextLong(2000, 4000))
                _state.value = _state.value.copy(isBugMode = false)
            }
        }
    }

    private fun startAxisLoop() {
        viewModelScope.launch {
            while (_state.value.isRunning) {
                delay(Random.nextLong(15000, 20000))
                if (!_state.value.isRunning) break
                val newAxis = listOf(RequiredAxis.X, RequiredAxis.Y, RequiredAxis.ANY).random()
                _state.value = _state.value.copy(requiredAxis = newAxis)
            }
        }
    }

    private fun startHeatMode() {
        val cores = Runtime.getRuntime().availableProcessors()
        repeat(cores) {
            val job = viewModelScope.launch(Dispatchers.Default) {
                while (_state.value.isRunning) {
                    var pi = 0.0
                    var sign = 1.0
                    for (i in 0..1_000_000) {
                        val denominator = (2 * i + 1).toDouble()
                        pi += sign * (4.0 / denominator)
                        sign = -sign
                    }
                    delay(50)
                }
            }
            heatJobs.add(job)
        }
    }

    private fun stopAllJobs() {
        heatJobs.forEach { it.cancel() }
        heatJobs.clear()
        powerUpTimerJob?.cancel()
        nitrogenTimerJob?.cancel()
        currentEventJob?.cancel()
    }

    fun resetGame() {
        stopAllJobs()
        _state.value = GameState()
    }

    override fun onCleared() {
        stopAllJobs()
        super.onCleared()
    }
}