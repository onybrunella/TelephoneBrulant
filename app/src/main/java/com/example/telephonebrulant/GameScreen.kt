package com.example.telephonebrulant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val state by viewModel.state.collectAsState()

    // --- LOGIQUE D'ANIMATION DU TREMBLEMENT ---
    val infiniteTransition = rememberInfiniteTransition(label = "shake")

    // Création d'une oscillation infinie entre -1 et 1
    val shakeMultiplier by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(40, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "shakeAnim"
    )

    // Calcul de l'intensité du décalage (offset) selon l'état du jeu
    val shakeOffset = when {
        !state.isRunning -> 0.dp
        state.isBugMode -> (shakeMultiplier * 6).dp // Très fort tremblement en mode bug
        state.heat > 80f -> (shakeMultiplier * 3).dp // Tremblement de surchauffe
        state.heat > 60f -> (shakeMultiplier * 1.5f).dp // Petit tremblement
        else -> 0.dp
    }

    // Animation de la couleur de fond
    val backgroundColor by animateColorAsState(
        targetValue = when {
            state.isGameOver -> Color(0xFF200000)
            state.heat < 40f -> Color(0xFF1A6BB5)
            state.heat < 75f -> Color(0xFFE07B00)
            else -> Color(0xFFB71C1C)
        },
        animationSpec = tween(500), label = "BgColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .offset(y = shakeOffset), // Application du tremblement sur l'axe Y
        contentAlignment = Alignment.Center
    ) {
        when {
            state.isGameOver -> GameOverScreen(state.score) { viewModel.resetGame() }
            !state.isRunning -> StartScreen { viewModel.startGame() }
            else -> PlayingScreen(state)
        }
    }
}

@Composable
fun PlayingScreen(state: GameState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // En-tête : Score et Multiplicateur
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SCORE: ${state.score}", color = Color.White, fontWeight = FontWeight.Bold)
            val mult = when {
                state.heat > 80f -> "x4"
                state.heat > 60f -> "x2"
                else -> "x1"
            }
            Text("BONUS $mult", color = Color.White.copy(alpha = 0.7f))
        }

        // Centre : Affichage de la température et alertes
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${state.heat.toInt()}°C",
                fontSize = 100.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            HeatBar(heat = state.heat)

            Spacer(modifier = Modifier.height(32.dp))

            // Alertes contextuelles
            if (state.isBugMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Cyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "⚠️ BUG DÉTECTÉ : NE BOUGEZ PLUS !",
                        color = Color.Cyan,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (state.currentEvent != GameEvent.NONE) {
                val eventDesc = when(state.currentEvent) {
                    GameEvent.COOLING_BOOST -> "❄️ BOOST : REFROIDISSEMENT X2"
                    GameEvent.OVERCLOCK -> "🔥 ALERTE : CHAUFFE X2"
                    GameEvent.SENSOR_CRAZY -> "📡 CAPTEUR INSTABLE"
                    GameEvent.INSTABILITY -> "🧬 SYSTÈME INSTABLE"
                    else -> ""
                }
                Text(eventDesc, color = Color.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            }
        }

        // Bas de l'écran : Instruction dynamique
        Text(
            text = if (state.isBugMode) "ARRÊTEZ TOUT !" else "SECOUEZ LE TÉLÉPHONE !",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun HeatBar(heat: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.3f))
    ) {
        val animatedWidth by animateFloatAsState(
            targetValue = heat / 100f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "BarAnim"
        )

        // Couleur de la barre qui change selon la chaleur
        val barColor = when {
            heat > 80f -> Color.White // Flash blanc quand c'est critique
            heat > 60f -> Color(0xFFFFCC00) // Orange
            else -> Color(0xFF4CAF50) // Vert
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(animatedWidth)
                .fillMaxHeight()
                .background(barColor)
        )
    }
}

@Composable
fun StartScreen(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "TÉLÉPHONE\nBRÛLANT",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            lineHeight = 44.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStart,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
        ) {
            Text("DÉMARRER LE JEU", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun GameOverScreen(score: Int, onRestart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("💥 EXPLOSION ! 💥", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.Red)
        Spacer(modifier = Modifier.height(8.dp))
        Text("SCORE FINAL : $score", color = Color.White, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("RÉESSAYER", fontWeight = FontWeight.Bold)
        }
    }
}