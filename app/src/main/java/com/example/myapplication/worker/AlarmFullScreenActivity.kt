package com.example.myapplication.worker

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme

class AlarmFullScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val titulo      = intent.getStringExtra(AlarmSoundService.EXTRA_TITULO) ?: "Cobro pendiente"
        val descripcion = intent.getStringExtra(AlarmSoundService.EXTRA_DESCRIPCION) ?: ""

        setContent {
            MyApplicationTheme {
                BackHandler(enabled = AlarmSoundService.estaActiva) { }
                PantallaAlarmaActiva(
                    titulo      = titulo,
                    descripcion = descripcion,
                    onApagar    = { apagarYCerrar() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!AlarmSoundService.estaActiva) finish()
    }

    private fun apagarYCerrar() {
        startService(
            Intent(this, AlarmSoundService::class.java).apply {
                action = AlarmSoundService.ACTION_STOP
            }
        )
        finish()
    }
}

@Composable
private fun PantallaAlarmaActiva(titulo: String, descripcion: String, onApagar: () -> Unit) {
    val pulso = rememberInfiniteTransition(label = "pulso")
    val escala by pulso.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.18f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label         = "escala"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier            = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(escala)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Alarm,
                        contentDescription = null,
                        modifier           = Modifier.size(58.dp),
                        tint               = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text       = titulo,
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    color      = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(24.dp))

                Card(
                    shape  = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Text(
                        text       = descripcion,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign  = TextAlign.Center,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier   = Modifier.padding(horizontal = 28.dp, vertical = 22.dp)
                    )
                }
            }

            Button(
                onClick  = onApagar,
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 52.dp)
            ) {
                Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Apagar alarma", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
