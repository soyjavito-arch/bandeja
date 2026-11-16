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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
            MaterialTheme(colorScheme = darkColorScheme(primary = Azul, background = Fondo, surface = Fondo)) {
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
    var tab by rememberSaveable { mutableStateOf("gym") }
    // Con sesión de gym abierta, la notificación fija mantiene el descanso aunque cambies de app.
    LaunchedEffect(v) {
        val s = Gym.sesion()
        if (s.inicio > 0 && s.fin == 0L) DescansoService.arrancar(ctx)
    }
    Scaffold(
        containerColor = Fondo,
        bottomBar = {
            NavigationBar(containerColor = Tarjeta) {
                listOf("gym" to "🏋️  Gym", "padel" to "🎾  Pádel", "bici" to "🚴  Bici").forEach { (id, txt) ->
                    NavigationBarItem(
                        selected = tab == id, onClick = { tab = id },
                        icon = {}, label = { Text(txt, fontSize = 15.sp) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Tarjeta2),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().background(Fondo).padding(pad)) {
            when (tab) {
                "padel" -> PadelTab(v)
                "bici" -> BiciTab(v)
                else -> GymTab(v, onIr = { tab = it })
            }
        }
    }
}
