package com.bandeja.padel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.bandeja.shared.Docs
import com.bandeja.shared.PadelVivo
import com.bandeja.shared.Partidos
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var store: MatchStore
    private lateinit var hrLog: HrLog
    private val saved = mutableStateOf(Saved())
    private val heartRate = mutableIntStateOf(0)
    private val kcal = mutableIntStateOf(0)
    private var sensorManager: SensorManager? = null
    private var hrSensor: Sensor? = null

    /** Pantalla de inicio: "inicio", "padel", "gym" o "bici". */
    private val modo = mutableStateOf("inicio")
    private var ultimoVivo = 0L

    private val askBodySensors =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startHeartRate()
        }

    private var trasPermisos: (() -> Unit)? = null
    private val askVarios =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            trasPermisos?.invoke(); trasPermisos = null
        }

    /** Pide lo que haga falta para gym/bici y luego sigue (si se niega algo, sigue igual con lo que haya). */
    private fun pedirPermisos(gps: Boolean, polar: Boolean, luego: () -> Unit) {
        val lista = buildList {
            add(Manifest.permission.BODY_SENSORS)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (polar && android.os.Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (gps || (polar && android.os.Build.VERSION.SDK_INT < 31)) add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (lista.isEmpty()) { luego(); return }
        trasPermisos = luego
        askVarios.launch(lista.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MatchStore(this)
        hrLog = HrLog(this)
        saved.value = store.load()
        refreshKcal()
        applyKeepScreenOn()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        hrSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (hrSensor != null && !hasBodySensors()) {
            askBodySensors.launch(Manifest.permission.BODY_SENSORS)
        }

        // Si hay un partido a medias se abre directamente el marcador.
        val s0 = saved.value
        if (s0.serverPicked && !s0.state().isFinished) modo.value = "padel"
        else if (com.bandeja.shared.Gym.sesion().let { it.inicio > 0 && it.fin == 0L }) modo.value = "gym"
        else if (EntrenoService.biciActiva.value) modo.value = "bici"

        // Órdenes del marcador desde el móvil: A (punto nuestro), B (punto rival), U (deshacer).
        Ordenes.padel = { cmd ->
            runOnUiThread {
                when (cmd) {
                    "A" -> addPoint(Team.A)
                    "B" -> addPoint(Team.B)
                    "U" -> undo()
                }
            }
        }
        publicarVivo(forzar = true)
        pulsoAlMovil(intent)
        pedirPulsoFondo()

        setContent {
            when (modo.value) {
                "padel" -> BandejaApp(
                    saved = saved.value,
                    heartRate = heartRate.intValue,
                    kcal = kcal.intValue,
                    onPoint = ::addPoint,
                    onUndo = ::undo,
                    onChange = ::update,
                    onExport = { exportMatch(showToast = true) },
                    onNuevo = ::nuevoPartido,
                    onInicio = { modo.value = "inicio" },
                )
                "gym" -> GymReloj(onSalir = { modo.value = "inicio" })
                "bici" -> BiciReloj(onSalir = { modo.value = "inicio" }, pedirPermisos = ::pedirPermisos)
                else -> InicioReloj(
                    onPadel = { modo.value = "padel" },
                    onGym = { pedirPermisos(false, false) { modo.value = "gym" } },
                    onBici = { modo.value = "bici" },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Ordenes.padel = null
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        pulsoAlMovil(intent)
    }

    /** Se tocó el aviso «Bici en el móvil»: empieza a mandar el pulso. */
    private fun pulsoAlMovil(i: android.content.Intent?) {
        if (i?.getBooleanExtra("pulsoMovil", false) != true) return
        i.removeExtra("pulsoMovil")
        EntrenoService.orden(this, "pulso:on")
        Toast.makeText(this, "Mandando el pulso al móvil", Toast.LENGTH_SHORT).show()
    }

    private val askFondo = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** Una vez: permiso para leer el pulso con la app cerrada (lo pide el móvil en la bici). */
    private fun pedirPulsoFondo() {
        if (android.os.Build.VERSION.SDK_INT < 33 || !hasBodySensors()) return
        val p = "android.permission.BODY_SENSORS_BACKGROUND"
        if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("permisos", MODE_PRIVATE)
        if (prefs.getBoolean("fondo", false)) return
        prefs.edit().putBoolean("fondo", true).apply()
        askFondo.launch(p)
    }

    /** «Nuevo partido» y «Nuevo partido a 1 set»: mismos ajustes, cambia solo el número de sets. */
    private fun nuevoPartido(unSet: Boolean) {
        val cur = saved.value
        // Si había un partido a medias se guarda antes de borrarlo.
        if (cur.history.isNotEmpty() && !cur.state().isFinished) exportMatch(showToast = false)
        val reglas = cur.rules.copy(setsToWin = if (unSet) 1 else 2)
        update(cur.copy(rules = reglas, history = emptyList(), timerStartedAt = 0L, timerAccumMs = 0L,
            serverChosen = false, endChangesSeen = 0, orderA = "", orderB = "", matchStartedAt = 0L))
        hrLog.clear()
        refreshKcal()
        modo.value = "padel"
    }

    /** Marcador en directo para el móvil. */
    private fun publicarVivo(forzar: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forzar && now - ultimoVivo < 5000) return
        ultimoVivo = now
        val s = saved.value
        val st = s.state()
        runCatching {
            Docs.put(PadelVivo.DOC, JSONObject().apply {
                put("activo", s.serverPicked)
                put("sets", JSONArray().apply { st.sets.forEach { put(JSONArray().apply { put(it.a); put(it.b) }) } })
                put("juegos", JSONArray().apply { put(st.gamesA); put(st.gamesB) })
                put("puntos", JSONArray().apply { put(st.pointLabel(Team.A)); put(st.pointLabel(Team.B)) })
                put("saque", if (st.server == Team.A) "azul" else "verde")
                put("tiebreak", st.tieBreak)
                put("aviso", st.decidingLabel ?: JSONObject.NULL)
                put("terminado", st.isFinished)
                put("ganador", st.winner?.let { if (it == Team.A) "azul" else "verde" } ?: JSONObject.NULL)
                put("unSet", s.rules.setsToWin == 1)
                put("acum", s.timerAccumMs)
                put("desde", s.timerStartedAt)
                put("inicio", s.matchStartedAt)
                put("hr", heartRate.intValue)
                put("kcal", kcal.intValue)
                put("t", now)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        startHeartRate()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    // ---- Marcador ----

    private fun addPoint(team: Team) {
        val cur = saved.value
        // El primer toque del partido elige quién saca y no puntúa.
        if (!cur.serverPicked) {
            // El cronómetro arranca al elegir quién saca.
            update(cur.copy(firstServer = team, serverChosen = true).startTimer(System.currentTimeMillis()))
            vibrate(longArrayOf(0, 60, 60, 60))
            return
        }
        val before = cur.state()
        if (before.isFinished) return
        val now = System.currentTimeMillis()
        var next = cur.copy(history = cur.history + team).startTimer(now)
        val after = next.state()
        if (after.isFinished) next = next.pauseTimer(now)
        update(next)
        // Al terminar el partido se exporta solo.
        if (after.isFinished) exportMatch(showToast = true)
        when {
            after.isFinished -> vibrate(longArrayOf(0, 200, 120, 200, 120, 400))
            after.endChanges > before.endChanges -> vibrate(longArrayOf(0, 400, 150, 400))
            after.isDecidingPoint && !before.isDecidingPoint ->
                vibrate(longArrayOf(0, 80, 70, 80, 70, 80, 70, 250))
            after.sets.size > before.sets.size -> vibrate(longArrayOf(0, 150, 100, 150))
            after.games(team) > before.games(team) || after.tieBreak != before.tieBreak ->
                vibrate(longArrayOf(0, 120))
            else -> vibrate(longArrayOf(0, 35))
        }
    }

    private fun undo() {
        val cur = saved.value
        if (cur.history.isEmpty()) {
            // Sin puntos todavía: deshacer vuelve a preguntar quién saca.
            if (cur.serverChosen) {
                update(cur.copy(serverChosen = false, timerStartedAt = 0L, timerAccumMs = 0L))
            }
            return
        }
        val wasFinished = cur.state().isFinished
        var next = cur.copy(history = cur.history.dropLast(1))
        next = next.copy(endChangesSeen = minOf(next.endChangesSeen, next.state().endChanges))
        if (wasFinished) next = next.startTimer(System.currentTimeMillis())
        update(next)
        vibrate(longArrayOf(0, 20, 60, 20))
    }

    private fun update(new: Saved) {
        var s = new
        val old = saved.value
        // Empieza un partido (se elige quién saca): se apunta la hora y se reinicia el pulso.
        if (!old.serverPicked && s.serverPicked) {
            s = s.copy(matchStartedAt = System.currentTimeMillis())
            hrLog.clear()
        }
        val keepChanged = s.keepScreenOn != old.keepScreenOn
        saved.value = s
        store.save(s)
        if (s.weightKg != old.weightKg || s.matchStartedAt != old.matchStartedAt) refreshKcal()
        if (keepChanged) applyKeepScreenOn()
        publicarVivo(forzar = true)
    }

    private fun applyKeepScreenOn() {
        if (saved.value.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun vibrate(pattern: LongArray) {
        val v: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching { v?.vibrate(VibrationEffect.createWaveform(pattern, -1)) }
    }

    private fun refreshKcal() {
        val s = saved.value
        val start = if (s.matchStartedAt > 0) s.matchStartedAt else System.currentTimeMillis()
        kcal.intValue = Calories.total(hrLog.samples(), s.weightKg, Calories.age(s.birthYear, start))
    }

    // ---- Exportar ----

    private fun exportMatch(showToast: Boolean) {
        val name = MatchExport.save(this, saved.value, hrLog.samples())
        // También al historial compartido con el móvil.
        runCatching {
            val j = MatchExport.json(saved.value, hrLog.samples(), System.currentTimeMillis())
            val (key, doc) = Partidos.desdeExport(j, saved.value.rules.setsToWin == 1)
            Docs.get(key)?.optString("nota")?.takeIf { it.isNotEmpty() }?.let { doc.put("nota", it) }
            Docs.put(key, doc)
        }
        if (showToast) {
            val msg = if (name != null) "Partido guardado:\n$name" else "No se pudo guardar"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    // ---- Pulso ----

    private fun hasBodySensors() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    private fun startHeartRate() {
        val s = hrSensor ?: return
        if (!hasBodySensors()) return
        sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val bpm = event.values.firstOrNull()?.toInt() ?: 0
            if (bpm > 0) {
                heartRate.intValue = bpm
                if (modo.value == "padel") publicarVivo()
                // Se guarda el pulso mientras el partido está en marcha.
                val s = saved.value
                if (s.serverPicked && s.timerRunning && !s.state().isFinished) {
                    if (hrLog.add(s.elapsedMs(System.currentTimeMillis()) / 1000, bpm)) refreshKcal()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
