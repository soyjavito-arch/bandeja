package com.bandeja.movil

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.bandeja.shared.Docs
import com.bandeja.shared.Gym
import com.bandeja.shared.Sync

class MainActivity : ComponentActivity() {
    private val pedirAvisos = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pedirAvisos.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Azul, background = Fondo, surface = Tarjeta, onSurface = Tinta, onBackground = Tinta)) {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Sync.comprobarConexion()
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val v by Docs.version.collectAsState()
    var seccion by rememberSaveable { mutableStateOf("inicio") }
    // Con sesión de gym abierta, la notificación fija mantiene el descanso aunque cambies de app.
    LaunchedEffect(v) {
        val s = Gym.sesion()
        if (s.inicio > 0 && s.fin == 0L) DescansoService.arrancar(ctx)
    }
    // En Gym y Bici la pantalla no se apaga mientras la app está delante (al minimizarla, sí).
    DisposableEffect(seccion) {
        val w = (ctx as? android.app.Activity)?.window
        if (seccion == "gym" || seccion == "bici") w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }
    Box(Modifier.fillMaxSize().background(Fondo).systemBarsPadding()) {
        when (seccion) {
            "gym" -> { BackHandler { seccion = "inicio" }; GymTab(v, onInicio = { seccion = "inicio" }) }
            "padel" -> { BackHandler { seccion = "inicio" }; PadelTab(v, onInicio = { seccion = "inicio" }) }
            "bici" -> { BackHandler { seccion = "inicio" }; BiciTab(v, onInicio = { seccion = "inicio" }) }
            else -> Inicio { seccion = it }
        }
    }
}

/** Al abrir la app: eliges qué toca. */
@Composable
private fun Inicio(onElegir: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Spacer(Modifier.height(30.dp))
        Text("¿QUÉ TOCA HOY?", color = Tinta, fontSize = 36.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        listOf(
            Triple("gym", "🏋️  GYM", "Empuje · Pierna · Tirón") to Azul,
            Triple("padel", "🎾  PÁDEL", "Partidos y marcador") to Verde,
            Triple("bici", "🚴  BICI", "Spinning con el Polar") to Naranja,
        ).forEach { (t, color) ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).height(130.dp).clip(RoundedCornerShape(20.dp))
                    .background(color).clickable { onElegir(t.first) }.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(t.second, color = Color.White, fontSize = 40.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                Text(t.third, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
            }
        }
    }
}
