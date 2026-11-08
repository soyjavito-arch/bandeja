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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var store: MatchStore
    private val saved = mutableStateOf(Saved())
    private val heartRate = mutableIntStateOf(0)
    private var sensorManager: SensorManager? = null
    private var hrSensor: Sensor? = null

    private val askBodySensors =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) startHeartRate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MatchStore(this)
        saved.value = store.load()
        applyKeepScreenOn()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        hrSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (hrSensor != null && !hasBodySensors()) {
            askBodySensors.launch(Manifest.permission.BODY_SENSORS)
        }

        setContent {
            BandejaApp(
                saved = saved.value,
                heartRate = heartRate.intValue,
                onPoint = ::addPoint,
                onUndo = ::undo,
                onChange = ::update,
            )
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
        val before = cur.state()
        if (before.isFinished) return
        val now = System.currentTimeMillis()
        var next = cur.copy(history = cur.history + team).startTimer(now)
        val after = next.state()
        if (after.isFinished) next = next.pauseTimer(now)
        update(next)
        when {
            after.isFinished -> vibrate(longArrayOf(0, 200, 120, 200, 120, 400))
            after.sets.size > before.sets.size -> vibrate(longArrayOf(0, 150, 100, 150))
            after.games(team) > before.games(team) || after.tieBreak != before.tieBreak ->
                vibrate(longArrayOf(0, 120))
            else -> vibrate(longArrayOf(0, 35))
        }
    }

    private fun undo() {
        val cur = saved.value
        if (cur.history.isEmpty()) return
        val wasFinished = cur.state().isFinished
        var next = cur.copy(history = cur.history.dropLast(1))
        if (wasFinished) next = next.startTimer(System.currentTimeMillis())
        update(next)
        vibrate(longArrayOf(0, 20, 60, 20))
    }

    private fun update(s: Saved) {
        val keepChanged = s.keepScreenOn != saved.value.keepScreenOn
        saved.value = s
        store.save(s)
        if (keepChanged) applyKeepScreenOn()
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
            if (bpm > 0) heartRate.intValue = bpm
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
