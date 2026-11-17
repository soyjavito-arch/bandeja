package com.bandeja.movil

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.bandeja.shared.Bici
import com.bandeja.shared.Docs
import com.bandeja.shared.Fechas
import com.bandeja.shared.Kcal
import com.bandeja.shared.PolarBle
import com.bandeja.shared.Sync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Bici grabada en el MÓVIL, sin abrir nada en el reloj:
 * - "polar": el móvil se conecta él solo al Polar H10 por Bluetooth.
 * - "reloj": el móvil pide al reloj que le mande su pulso (el reloj lo hace en segundo plano).
 * En los cambios de ritmo el móvil DICE «Start», «Break» y «Finish» (el reloj solo vibra).
 */
class BiciService : Service() {

    companion object {
        private const val CANAL = "bici"
        private const val ID = 9
        const val PULSO_RELOJ = "/hr"

        private val _activa = MutableStateFlow(false)
        /** Hay una bici grabándose en el móvil. */
        val activa: StateFlow<Boolean> = _activa
        private val _estado = MutableStateFlow("")
        /** Estado del Polar o del reloj, para enseñarlo en pantalla. */
        val estado: StateFlow<String> = _estado

        @Volatile private var hrReloj = 0
        @Volatile private var tReloj = 0L

        /** Pulso que manda el reloj (cada segundo). */
        fun pulsoReloj(bpm: Int) {
            if (bpm > 0) { hrReloj = bpm; tReloj = System.currentTimeMillis() }
        }

        fun empezar(ctx: Context, polar: Boolean) = enviar(ctx, "start", if (polar) "polar" else "reloj")
        fun pausa(ctx: Context) = enviar(ctx, "pausa", null)
        fun parar(ctx: Context) = enviar(ctx, "parar", null)

        private fun enviar(ctx: Context, accion: String, x: String?) {
            runCatching { ctx.startForegroundService(Intent(ctx, BiciService::class.java).setAction(accion).putExtra("x", x)) }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private var ttsListo = false
    private var g: Grabacion? = null
    private var tickN = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Docs.init(this)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CANAL, "Bici en marcha", NotificationManager.IMPORTANCE_LOW))
        tts = TextToSpeech(applicationContext) { st ->
            ttsListo = st == TextToSpeech.SUCCESS
            if (ttsListo) tts?.language = Locale.ENGLISH
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enPrimerPlano("Bici", "Preparando…")
        when (intent?.action) {
            "start" -> if (g == null) {
                g = Grabacion(intent.getStringExtra("x") != "reloj", Bici.ajustes())
                _activa.value = true
            }
            "pausa" -> g?.pausar()
            "parar" -> { g?.terminar(); g = null }
        }
        if (g == null) { cerrar(); return START_NOT_STICKY }
        if (wake == null) {
            wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bandeja:bici").apply { acquire(4 * 3600_000L) }
        }
        main.removeCallbacks(bucle)
        main.post(bucle)
        return START_STICKY
    }

    private val bucle = object : Runnable {
        override fun run() {
            tickN++
            val gg = g ?: run { cerrar(); return }
            gg.tick(System.currentTimeMillis())
            main.postDelayed(this, 1000)
        }
    }

    private fun cerrar() {
        main.removeCallbacks(bucle)
        _activa.value = false
        _estado.value = ""
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        g?.terminar(); g = null
        _activa.value = false
        main.removeCallbacksAndMessages(null)
        wake?.let { if (it.isHeld) it.release() }
        tts?.shutdown()
        super.onDestroy()
    }

    private fun decir(t: String) {
        if (ttsListo) tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, t)
    }

    private fun vibrar(p: LongArray) {
        runCatching {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                .vibrate(VibrationEffect.createWaveform(p, -1))
        }
    }

    private fun notificacion(titulo: String, texto: String): Notification {
        val abrir = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java)
            .putExtra("seccion", "bici").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo).setContentText(texto)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(abrir)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .build()
    }

    private fun tiene(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun enPrimerPlano(titulo: String, texto: String) {
        val n = notificacion(titulo, texto)
        var tipos = 0
        if (tiene(Manifest.permission.BLUETOOTH_CONNECT)) tipos = tipos or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (tiene(Manifest.permission.ACCESS_FINE_LOCATION)) tipos = tipos or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        runCatching { startForeground(ID, n, if (tipos != 0) tipos else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
            .recoverCatching { startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) }
    }

    /** Una sesión de bici grabada en el móvil (mismo formato que la del reloj). */
    @SuppressLint("MissingPermission")
    private inner class Grabacion(conPolar: Boolean, val aj: Bici.Ajustes) {
        val inicio = System.currentTimeMillis()
        var acumulado = 0L
        var desde = inicio
        var pausa = false
        var fuente = if (conPolar) "polar" else "reloj"
        var hrPolar = 0
        var ultimoPolar = 0L
        val muestras = JSONArray()
        var suma = 0L
        var n = 0
        var max = 0
        var kcal = 0.0
        val hrs = ArrayDeque<Int>()
        var km = 0.0
        var kmh: Double? = null
        var ultimaPos: Location? = null
        // Siempre existe; se enciende/apaga y ajusta en plena bici desde la pantalla.
        // Empieza en pausa: se arranca con el botón «Empezar» de la caja de cambios de ritmo.
        val ritmo = Bici.Ritmo(aj.n, aj.lo, aj.hi).apply { activo = false; Bici.guardar(aj.copy(marcha = false)) }
        var polar: PolarBle? = null
        var lm: LocationManager? = null
        var ultimoSeg = -1L
        var ultimoPing = 0L

        val gps = LocationListener { loc ->
            if (pausa) return@LocationListener
            kmh = if (loc.hasSpeed()) loc.speed * 3.6 else null
            val prev = ultimaPos
            if (loc.accuracy < 30f) {
                if (prev != null) km += prev.distanceTo(loc) / 1000.0
                ultimaPos = loc
            }
        }

        init {
            if (conPolar) {
                polar = PolarBle(this@BiciService, onPulso = { b -> hrPolar = b; ultimoPolar = System.currentTimeMillis() },
                    onEstado = { _estado.value = "Polar: $it" }).also { it.empezar() }
            } else _estado.value = "Pidiendo el pulso al reloj…"
            // Siempre se pide el pulso al reloj: con el Polar sirve de reserva si se corta.
            Sync.enviar(Bici.CMD, "pulso:on")
            ultimoPing = inicio
            if (aj.gps && tiene(Manifest.permission.ACCESS_FINE_LOCATION)) {
                lm = getSystemService(LocationManager::class.java)
                runCatching { lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gps, Looper.getMainLooper()) }
            }
            publicar(inicio, 0)
        }

        fun seg(now: Long) = (acumulado + if (!pausa) now - desde else 0) / 1000

        fun pausar() {
            val now = System.currentTimeMillis()
            if (pausa) { pausa = false; desde = now } else { acumulado += now - desde; pausa = true }
            publicar(now, 0)
        }

        fun tick(now: Long) {
            // El reloj deja de mandar pulso si no se le recuerda cada poco.
            if (now - ultimoPing > 10_000) { ultimoPing = now; Sync.enviar(Bici.CMD, "pulso:on") }
            val polarVivo = polar != null && now - ultimoPolar < 5000
            val relojVivo = now - tReloj < 5000
            val nueva = when {
                polarVivo -> "polar"
                relojVivo -> "reloj"
                else -> fuente
            }
            if (nueva != fuente && now - inicio > 15_000) { fuente = nueva; vibrar(longArrayOf(0, 120, 80, 120)) }
            if (!polarVivo && polar == null) _estado.value = if (relojVivo) "Pulso del reloj" else "Esperando el pulso del reloj…"
            val hr = if (polarVivo) hrPolar else if (relojVivo) hrReloj else 0
            if (tickN % 2 == 0L) ritmo.ajustar(Bici.ajustes())
            val s = seg(now)
            if (!pausa && hr > 0 && s != ultimoSeg) {
                ultimoSeg = s
                suma += hr; n++
                if (hr > max) max = hr
                kcal += Kcal.porMinuto(hr) / 60.0
                if (s % 10 == 0L) muestras.put(JSONArray().apply { put(s); put(hr) })
                if (s % 5 == 0L) { hrs.addLast(hr); while (hrs.size > 72) hrs.removeFirst() }
                ritmo.pulso(hr, now)?.let { ev ->
                    when (ev) {
                        "start" -> { decir("Start"); vibrar(longArrayOf(0, 500)) }
                        "break" -> { decir("Break"); vibrar(longArrayOf(0, 200, 100, 200, 100, 200)) }
                        "finish" -> { decir("Finish"); vibrar(longArrayOf(0, 600, 200, 600, 200, 900)) }
                    }
                    publicar(now, hr)
                }
            }
            if (tickN % 2 == 0L) publicar(now, hr)
            if (tickN % 5 == 0L) getSystemService(NotificationManager::class.java).notify(ID,
                notificacion(if (aj.gps) "Bici · exterior" else "Bici · spinning",
                    "♥ ${if (hr > 0) hr else "--"} · ${Fechas.hms(s)}" + if (pausa) " · en pausa" else ""))
        }

        fun publicar(now: Long, hr: Int) {
            Docs.put(Bici.LIVE, JSONObject().apply {
                put("activa", true); put("pausa", pausa); put("inicio", inicio); put("seg", seg(now))
                put("hr", hr); put("fuente", fuente); put("donde", "movil")
                put("media", if (n > 0) (suma / n).toInt() else 0); put("max", max); put("kcal", kcal.toInt())
                put("km", if (aj.gps) km else JSONObject.NULL); put("kmh", if (aj.gps) (kmh ?: 0.0) else JSONObject.NULL)
                put("ritmo", ritmo.json(now))
                put("hrs", JSONArray(hrs.toList()))
                put("t", now)
            })
        }

        fun terminar() {
            val now = System.currentTimeMillis()
            polar?.parar()
            runCatching { lm?.removeUpdates(gps) }
            Sync.enviar(Bici.CMD, "pulso:off")
            val dur = seg(now)
            if (dur >= 30) {
                Docs.put("bici-" + Fechas.clave(inicio), JSONObject().apply {
                    put("fecha", Fechas.dia(inicio)); put("inicio", Fechas.iso(inicio)); put("dur", dur)
                    put("km", if (aj.gps) Math.round(km * 100) / 100.0 else JSONObject.NULL)
                    put("kcal", kcal.toInt())
                    put("pm", if (n > 0) (suma / n).toInt() else JSONObject.NULL)
                    put("pmax", if (max > 0) max else JSONObject.NULL)
                    put("hr", muestras)
                    put("src", fuente)
                    put("modo", if (aj.gps) "exterior" else "spinning")
                    ritmo.takeIf { it.hechos.isNotEmpty() }?.let { r ->
                        put("cambios", JSONArray().apply { r.hechos.forEach { put(JSONArray().apply { put(it.subidaMs / 1000); put(it.recMs / 1000) }) } })
                        put("cambiosCfg", JSONObject().apply { put("n", r.n); put("lo", r.lo); put("hi", r.hi) })
                    }
                })
            }
            Docs.put(Bici.LIVE, JSONObject().apply { put("activa", false); put("t", now) })
        }
    }
}
