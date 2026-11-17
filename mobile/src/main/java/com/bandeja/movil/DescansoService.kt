package com.bandeja.movil

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import com.bandeja.shared.Fechas
import com.bandeja.shared.Gym
import com.bandeja.shared.Rutina

/**
 * Mientras hay sesión de gym abierta: notificación fija con el descanso (cuenta atrás)
 * y aviso al acabar, aunque cambies de app o apagues la pantalla.
 */
class DescansoService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var avisado = ""
    private var ultimoTexto = ""
    private var n = 0L

    /** Sin el reloj, el móvil apunta el tiempo y unas kcal estimadas (pulso típico de gym, 110). */
    private fun registrar(s: Gym.Sesion) {
        if (com.bandeja.shared.Pulso.leer() != null) return // el reloj mide de verdad: lo apunta él
        val min = s.duracionMs(System.currentTimeMillis()) / 60000.0
        Gym.registrar(s, Math.round(com.bandeja.shared.Kcal.porMinuto(110) * min).toInt(), null, null, true)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        canal(this)
        val n = notificacion("Gym en marcha", "")
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID, n)
        }
        h.removeCallbacksAndMessages(null)
        h.post(tick)
        return START_STICKY
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            Gym.tick(now)
            val s = Gym.sesion()
            if (s.inicio == 0L || s.fin > 0) {
                if (s.fin > 0) registrar(s)
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
            }
            if (++n % 15 == 0L) registrar(s)
            val t = s.timer
            val nombre = t?.let { tt ->
                val d = Rutina.dia(tt.key.substringBefore('-'))
                d?.ejercicios?.firstOrNull { Gym.clave(d.id, it) == tt.key }?.nombre
            } ?: ""
            val (titulo, texto) = when {
                t == null -> "Gym · ${Fechas.hms(s.duracionMs(now) / 1000)}" to (if (s.pausada) "Sesión en pausa" else "Sesión en marcha")
                t.pausa -> "Descanso en pausa · ${Fechas.mmss(t.restanteMs(now) / 1000)}" to nombre
                t.terminado(now) -> "¡Descanso terminado!" to nombre
                t.tipo == "trabajo" -> "Serie ${t.serie + 1} · quedan ${Fechas.mmss((t.restanteMs(now) + 999) / 1000)}" to nombre
                else -> "Descanso · ${Fechas.mmss((t.restanteMs(now) + 999) / 1000)}" to nombre
            }
            val id = t?.let { "${it.key}-${it.serie}-${it.tipo}" } ?: ""
            if (t != null && t.tipo == "descanso" && t.terminado(now) && avisado != id) {
                avisado = id
                avisar()
            }
            if ("$titulo|$texto" != ultimoTexto) {
                ultimoTexto = "$titulo|$texto"
                getSystemService(NotificationManager::class.java).notify(ID, notificacion(titulo, texto))
            }
            h.postDelayed(this, 1000)
        }
    }

    private fun avisar() {
        runCatching {
            val v = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 400, 150, 600), -1))
        }
        runCatching {
            RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
    }

    private fun notificacion(titulo: String, texto: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val ID = 7
        private const val CANAL = "gym"

        fun canal(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CANAL) == null) {
                nm.createNotificationChannel(NotificationChannel(CANAL, "Gym y descansos", NotificationManager.IMPORTANCE_LOW))
            }
        }

        fun arrancar(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, DescansoService::class.java)) }
        }
    }
}
