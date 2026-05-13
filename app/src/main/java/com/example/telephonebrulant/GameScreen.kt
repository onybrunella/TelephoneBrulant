package com.example.telephonebrulant

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─── Couleurs ───────────────────────────────────────────────
private val ColorCold   = Color(0xFF121212)
private val ColorWarm   = Color(0xFF442200)
private val ColorHot    = Color(0xFF661100)
private val ColorDead   = Color(0xFF200000)

private val ColorTempCold   = Color(0xFFFFFFFF)
private val ColorTempWarm   = Color(0xFFFF9800)
private val ColorTempHot    = Color(0xFFFF1744)
private val ColorNitrogen  = Color(0xFF00BCD4)

// ────────────────────────────────────────────────────────────

@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            state.isGameOver -> ColorDead
            state.heat > 80f -> ColorHot
            state.heat > 50f -> ColorWarm
            else             -> ColorCold
        },
        animationSpec = tween(500),
        label = "BgColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "shake")
    val shakeOffset by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(80),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShakeOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(24.dp)
            .offset(y = if (state.heat > 90f || state.isBugMode) shakeOffset.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            !state.isRunning && !state.isGameOver -> StartScreen { heatMode ->
                viewModel.startGame(heatMode)
            }
            state.isGameOver -> GameOverScreen(state.score) { viewModel.resetGame() }
          //  else             -> PlayingScreen(state)
            else -> PlayingScreen(state=state, onPowerUpClick = {viewModel.onPowerUpCollected()})
        }
    }
}

// ─── POWER-UPP ───────────────────────────────────────────
@Composable
fun PowerUpWidget(powerUp: PowerUp, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn() + fadeIn()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val xOffset = maxWidth * powerUp.xPercent
            val yOffset = maxHeight * powerUp.yPercent

            Box(
                modifier = Modifier
                    .offset(x = xOffset, y = yOffset)
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when (powerUp.type) {
                            PowerUpType.ICE_PACK         -> Color(0xFF1565C0).copy(alpha = 0.9f)
                            PowerUpType.LIQUID_NITROGEN  -> Color(0xFF00BCD4).copy(alpha = 0.9f)
                            PowerUpType.ANTIVIRUS        -> Color(0xFF2E7D32).copy(alpha = 0.9f)
                        }
                    )
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (powerUp.type) {
                            PowerUpType.ICE_PACK        -> "🧊"
                            PowerUpType.LIQUID_NITROGEN -> "💠"
                            PowerUpType.ANTIVIRUS       -> "🔧"
                        },
                        fontSize = 28.sp
                    )
                    Text(
                        text = when (powerUp.type) {
                            PowerUpType.ICE_PACK        -> "-5°"
                            PowerUpType.LIQUID_NITROGEN -> "PAUSE 5s"
                            PowerUpType.ANTIVIRUS       -> "FIX"
                        },
                        fontSize = 10.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


// ─── ÉCRAN DE JEU ───────────────────────────────────────────

@Composable
fun PlayingScreen(state: GameState, onPowerUpClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {

        // ── Score (discret en haut) ──
        Text(
            text = "SCORE : ${state.score}",
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.5f)
        )

        // ── Centre ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Flamme qui grossit avec la chaleur
            val flameSize by animateFloatAsState(
                targetValue = when {
                    state.heat > 80f -> 72f
                    state.heat > 50f -> 52f
                    else -> 36f
                },
                animationSpec = tween(400),
                label = "FlameSize"
            )
            Text(
                text = "🔥",
                fontSize = flameSize.sp
            )

            // Température — couleur progressive
            val tempColor by animateColorAsState(
                targetValue = when {
                    state.heat > 80f -> ColorTempHot
                    state.heat > 50f -> ColorTempWarm
                    else -> ColorTempCold
                },
                animationSpec = tween(400),
                label = "TempColor"
            )
            Text(
                text = "${state.heat.toInt()}°",
                fontSize = 80.sp,
                fontWeight = FontWeight.Black,
                color = tempColor
            )

            // Jauge animée avec pulsation si danger
            HeatBar(heat = state.heat)

            // Multiplicateur
            val multiplier = when {
                state.heat > 80f -> "x4"
                state.heat > 60f -> "x2"
                else -> "x1"
            }
            Text(
                text = "Multiplicateur : $multiplier",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp
            )

            // Événement aléatoire
            AnimatedVisibility(
                visible = state.currentEvent != GameEvent.NONE,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Yellow.copy(alpha = 0.15f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color.Yellow.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        text = when (state.currentEvent) {
                            GameEvent.OVERCLOCK -> "\uD83C\uDF21\uFE0F SURCHAUFFE\nLa chaleur monte x2 !"
                            GameEvent.COOLING_BOOST -> "❄️ VENTILATION\nSecouer refroidit 2x plus !"
                            GameEvent.SENSOR_CRAZY -> "\uD83D\uDCF3 CAPTEUR FOU\nSecouez plus fort !"
                            GameEvent.INSTABILITY -> "⚡ COURT-CIRCUIT\nDes pics de chaleur arrivent !"
                            GameEvent.NONE -> ""
                        },
                        modifier = Modifier.padding(12.dp),
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }

            AnimatedVisibility(
                visible = state.isBugMode,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Text(
                    text = "⚠️ CONTRÔLES INVERSÉS \nSecouer fait monter la température !",
                    color = Color.Cyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )
            }
        }

        // ── Instruction en bas ──

        var axisFlash by remember { mutableStateOf(false) }
        LaunchedEffect(state.requiredAxis) {
            axisFlash = true
            delay(800)
            axisFlash = false
        }
        val axisScale by animateFloatAsState(
            targetValue = if (axisFlash) 1.3f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "AxisFlash"
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Compteur azote
            AnimatedVisibility(
                visible = state.isNitrogenActive,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "💠 PAUSE — ${state.nitrogenSeconds}s",
                    fontSize = 14.sp,
                    color = ColorNitrogen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Instruction
            Text(
                text = when {
                    state.isBugMode                        -> "ARRÊTEZ DE SECOUER !"
                    state.requiredAxis == RequiredAxis.X   -> "GAUCHE — DROITE"
                    state.requiredAxis == RequiredAxis.Y   -> "HAUT — BAS"
                    else                                   -> "SECOUEZ !"
                },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    state.isBugMode                        -> Color.Cyan
                    state.requiredAxis != RequiredAxis.ANY -> Color.Yellow
                    state.heat > 80f                       -> ColorTempHot
                    else                                   -> Color.White.copy(alpha = 0.7f)
                },
                modifier = Modifier.scale(axisScale)
            )
        }
    }

        // Power-up superposé
        state.activePowerUp?.let { powerUp ->
            PowerUpWidget(powerUp = powerUp, onClick = onPowerUpClick)
        }
    }
}
// ─── JAUGE ──────────────────────────────────────────────────

@Composable
fun HeatBar(heat: Float) {
    // Pulsation quand danger
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val animatedHeat by animateFloatAsState(
        targetValue = heat / 100f,
        animationSpec = tween(200),
        label = "HeatBar"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.1f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedHeat)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    when {
                        heat > 80f -> ColorTempHot.copy(alpha = if (heat > 80f) pulseAlpha else 1f)
                        heat > 60f -> Color(0xFFFF6600)
                        else       -> Color(0xFF4CAF50)
                    }
                )
        )
    }
}

// ─── ÉCRAN D'ACCUEIL ────────────────────────────────────────

@Composable
fun StartScreen(onStart: (Boolean) -> Unit) {
    var heatModeEnabled by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }

    if (showTutorial) {
        TutorialScreen(onBack = { showTutorial = false })
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "🔥", fontSize = 64.sp)
            Text(
                text = "TÉLÉPHONE\nBRÛLANT",
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
                lineHeight = 42.sp
            )
            Text(
                text = "Secouez pour refroidir votre téléphone avant qu'il explose ! Mais attention aux bugs... !",
                fontSize = 15.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            TextButton(onClick = { showTutorial = true }) {
                Text(
                    text = "Comment jouer ?",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (heatModeEnabled)
                        Color.Red.copy(alpha = 0.2f)
                    else
                        Color.White.copy(alpha = 0.05f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (heatModeEnabled) Color.Red.copy(alpha = 0.5f)
                    else Color.White.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Switch(
                        checked = heatModeEnabled,
                        onCheckedChange = { heatModeEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Red)
                    )
                    Column {
                        Text(
                            text = "Mode Surchauffe",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Chauffe le CPU réellement",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onStart(heatModeEnabled) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (heatModeEnabled) "DÉMARRER EN MODE SURCHAUFFE" else "DÉMARRER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
    // ─── TUTORIEL ───────────────────────────────────────────────
    @Composable
    fun TutorialScreen(onBack: () -> Unit) {

        data class TutoItem(val emoji: String, val title: String, val desc: String)

        val items = listOf(
            TutoItem("🔥", "Refroidissez !",
                "La température monte toute seule en permanence. Secouez le téléphone pour la faire baisser. Si elle atteint 100°, c'est game over."),
            TutoItem("⚠️", "Mode bug",
                "Les contrôles s'inversent sans prévenir, secouer chauffe au lieu de refroidir ! Arrêtez de secouer dès que vous voyez cette alerte."),
            TutoItem("↔️", "Axe imposé",
                "Le jeu peut imposer une direction de secousse. GAUCHE-DROITE = secouez horizontalement. HAUT-BAS = secouez verticalement. Le mauvais axe n'a aucun effet."),
            TutoItem("🧊", "Pack de glace",
                "Appuyez dessus pour faire chuter la température de 10° immédiatement."),
            TutoItem("💠", "Azote liquide",
                "Appuyez dessus pour geler la température pendant 5 secondes. Ni montée, ni descente."),
            TutoItem("🔧", "Antivirus",
                "Appuyez dessus pour annuler immédiatement le mode bug ou l'événement en cours."),
            TutoItem("🌡️", "Événements",
                "Toutes les quelques secondes, un évènement arrive : Surchauffe (chauffe x2), Capteur défaillant (secousses moins efficaces), Court-circuit (pics de chaleur soudains) ou Ventilation (refroidissement x2) si vous avez de la chance."),
            TutoItem("🏆", "Score",
                "Survivre dans la zone orange rapporte x2 points. Dans la zone rouge, x4. Jouez avec le feu pour scorer plus !")
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "COMMENT JOUER",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(items) { item ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(text = item.emoji, fontSize = 22.sp)
                        Column {
                            Text(
                                text = item.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = item.desc,
                                fontSize = 13.sp,
                                color = Color.Gray,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                }
            }

            // ── Bouton toujours visible en bas ──
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "COMPRIS !", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

// ─── ÉCRAN GAME OVER ────────────────────────────────────────

@Composable
fun GameOverScreen(score: Int, onRestart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Text(text = "💥", fontSize = 72.sp)
        Text(
            text = "EXPLOSION !",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = Color.Red
        )
        Text(
            text = "Score final",
            fontSize = 16.sp,
            color = Color.Gray
        )
        Text(
            text = "$score",
            fontSize = 56.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "REJOUER",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}