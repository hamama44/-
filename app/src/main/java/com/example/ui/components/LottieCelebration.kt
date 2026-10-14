package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.TrophyGold
import kotlin.random.Random

@Composable
fun LottieVictoryCelebration(
    modifier: Modifier = Modifier,
    animationAsset: String = "trophy_celebration.json"
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(animationAsset))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = 1.0f
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Elegant stadium particle confetti fallback
            StadiumConfettiFallback(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun StadiumConfettiFallback(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val animState by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confetti_fall"
    )

    val confettiColors = listOf(TrophyGold, EmeraldNeon, Color.White, Color(0xFF00E5FF), Color(0xFFFF3D57))

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        for (i in 0..25) {
            val randomX = ((i * 37) % width.toInt()).toFloat()
            val speed = 0.5f + ((i % 5) * 0.2f)
            val currentY = ((animState * speed * height) + (i * 20)) % height
            val color = confettiColors[i % confettiColors.size]
            val radius = 4f + (i % 3) * 2f

            drawCircle(
                color = color,
                radius = radius,
                center = Offset(randomX, currentY)
            )
        }
    }
}
