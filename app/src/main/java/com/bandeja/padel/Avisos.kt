package com.bandeja.padel

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Solo vibración: el reloj no hace ningún sonido (Javi entrena con cascos y música). */
object Avisos {
    private var tts: TextToSpeech? = null
    private var listo = false

    fun vibrar(context: Context, patron: LongArray) {
        val v: Vibrator? = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        runCatching { v?.vibrate(VibrationEffect.createWaveform(patron, -1)) }
    }

    fun preparar(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { st ->
            listo = st == TextToSpeech.SUCCESS
            if (listo) tts?.language = Locale.ENGLISH
        }
    }

    fun decir(context: Context, texto: String) {
        preparar(context)
        if (listo) tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, texto)
    }

    val FIN_DESCANSO = longArrayOf(0, 300, 150, 300, 150, 600)
    val START = longArrayOf(0, 500)
    val BREAK = longArrayOf(0, 200, 100, 200, 100, 200)
    val FINISH = longArrayOf(0, 600, 200, 600, 200, 900)
}
