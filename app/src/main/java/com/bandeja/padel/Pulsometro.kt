package com.bandeja.padel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pulso del sensor de la muñeca para el gym y la bici (cuando no está el Polar). */
object Pulsometro : SensorEventListener {
    private val _bpm = MutableStateFlow(0)
    val bpm: StateFlow<Int> = _bpm
    private var usuarios = 0
    private var sm: SensorManager? = null

    @Synchronized
    fun empezar(context: Context) {
        usuarios++
        if (usuarios > 1) return
        if (context.checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) return
        sm = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val s = sm?.getDefaultSensor(Sensor.TYPE_HEART_RATE) ?: return
        sm?.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
    }

    @Synchronized
    fun parar() {
        usuarios = (usuarios - 1).coerceAtLeast(0)
        if (usuarios == 0) {
            sm?.unregisterListener(this)
            _bpm.value = 0
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val b = event.values.firstOrNull()?.toInt() ?: 0
        if (b > 0) _bpm.value = b
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
