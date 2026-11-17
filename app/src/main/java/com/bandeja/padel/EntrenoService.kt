package com.bandeja.padel

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
import com.bandeja.shared.Bici
import com.bandeja.shared.Docs
import com.bandeja.shared.Fechas
import com.bandeja.shared.Gym
import com.bandeja.shared.Kcal
import com.bandeja.shared.PolarBle
import com.bandeja.shared.Pulso
import com.bandeja.shared.Rutina
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Servicio del reloj que sigue funcionando con la pantalla apagada o en otra app:
 * - Gym: cuenta los descansos y vibra al acabar, mide pulso y calorías de la sesión.
 * - Bici: graba la sesión con el Polar H10 (o el pulso del reloj), GPS opcional y cambios de ritmo.
 */
class EntrenoService : Service() {

    companion object {
        private const val CANAL = "entreno"
        private const val ID = 7

        private val _bici = MutableStateFlow(false)
        val biciActiva: StateFlow<Boolean> = _bici
        private val _polar = MutableStateFlow("")
        val polarEstado: StateFlow<String> = _polar
        /** Lo último de la bici, al segundo, para la pantalla del propio reloj. */
        private val _vivo = MutableStateFlow<JSONObject?>(null)
        val vivo: StateFlow<JSONObject?> = _vivo

        fun gym(ctx: Context) = arrancar(ctx, "gym", null)
        fun bici(ctx: Context, polar: Boolean) = arrancar(ctx, "bici", if (polar) "polar" else "reloj")
        fun biciPausa(ctx: Context) = arrancar(ctx, "pausa", null)
        fun biciParar(ctx: Context) = arrancar(ctx, "parar", null)

        /** Órdenes que manda el móvil: "pulso:on/off" (el móvil graba la bici), "start:…", "pausa", "parar". */
        fun orden(ctx: Context, cmd: String) {
            when {
                // Si ya está mandando el pulso basta con apuntar que el móvil sigue pidiéndolo.
                cmd == "pulso:on" -> if (pulsoActivo) pingMovil = System.currentTimeMillis()
                    else if (!arrancar(ctx, "pulso", null)) avisoAbrir(ctx)
                cmd == "pulso:off" -> pingMovil = 0L // el bucle lo para en el siguiente segundo
                cmd.startsWith("start") -> bici(ctx, !cmd.endsWith("reloj"))
                cmd == "pausa" -> biciPausa(ctx)
                cmd == "parar" -> biciParar(ctx)
            }
        }

        @Volatile private var pulsoActivo = false
        @Volatile private var pingMovil = 0L

        private fun arrancar(ctx: Context, accion: String, extra: String?): Boolean {
            val i = Intent(ctx, EntrenoService::class.java).setAction(accion).putExtra("x", extra)
            return runCatching { ctx.startForegroundService(i) }.isSuccess
        }

        /** Si Android no deja arrancar desde segundo plano: aviso para tocarlo y empezar a mandar el pulso. */
        private fun avisoAbrir(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("pulso", "Pulso al móvil", NotificationManager.IMPORTANCE_HIGH))
            val abrir = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).putExtra("pulsoMovil", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            runCatching {
                nm.notify(8, Notification.Builder(ctx, "pulso")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Bici en el móvil")
                    .setContentText("Toca para mandarle tu pulso")
                    .setContentIntent(abrir).setAutoCancel(true).setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_WORKOUT)
                    .build())
            }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var wake: PowerManager.WakeLock? = null
    private var gymActivo = false
    private var avisadoFin = 0L
    private var gymKcal = 0.0
    private var gymInicio = 0L
    private var gymSuma = 0L
    private var gymN = 0L
    private var gymMax = 0
    private var tickN = 0L
    private var grab: Grabacion? = null
    private var notifTexto = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Docs.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Entreno en marcha", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        enPrimerPlano("Entreno en marcha")
        when (intent?.action) {
            "gym" -> if (!gymActivo) {
                gymActivo = true
                Pulsometro.empezar(this)
            }
            "bici" -> if (grab == null) {
                grab = Grabacion(intent.getStringExtra("x") != "reloj", Bici.ajustes())
                _bici.value = true
            }
            "pulso" -> {
                pingMovil = System.currentTimeMillis()
                if (!pulsoActivo) { pulsoActivo = true; Pulsometro.empezar(this) }
                getSystemService(NotificationManager::class.java).cancel(8)
            }
            "pulsoOff" -> pararPulso()
            "pausa" -> grab?.pausar()
            "parar" -> {
                grab?.terminar()
                grab = null
                _bici.value = false
            }
        }
        if (wake == null) {
            wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bandeja:entreno").apply { acquire(4 * 3600_000L) }
        }
        main.removeCallbacks(bucle)
        main.post(bucle)
        return START_STICKY
    }

    private val bucle = object : Runnable {
        override fun run() {
            tickN++
            val now = System.currentTimeMillis()
            if (gymActivo) tickGym(now)
            grab?.tick(now)
            if (pulsoActivo) tickPulso(now)
            if (!gymActivo && grab == null && !pulsoActivo) {
                parar()
                return
            }
            main.postDelayed(this, 1000)
        }
    }

    private fun tickGym(now: Long) {
        Gym.tick(now)
        val s = Gym.sesion()
        if (s.inicio == 0L || s.fin > 0 || s.fecha != Fechas.hoy()) {
            // Sesión terminada (o no empezada): se deja de medir.
            if (s.fin > 0 || tickN > 30) {
                if (gymInicio != 0L) {
                    Pulso.publicar(Pulsometro.bpm.value, gymKcal.toInt())
                    if (s.inicio == gymInicio) Gym.registrar(s, gymKcal.toInt(), if (gymN > 0) (gymSuma / gymN).toInt() else null, gymMax.takeIf { it > 0 }, false)
                }
                gymActivo = false
                Pulsometro.parar()
            }
            return
        }
        if (gymInicio != s.inicio) {
            gymInicio = s.inicio
            val pr = getSharedPreferences("gym", MODE_PRIVATE)
            gymKcal = pr.getFloat("kcal_${s.inicio}", 0f).toDouble()
            gymSuma = pr.getLong("suma_${s.inicio}", 0L); gymN = pr.getLong("n_${s.inicio}", 0L); gymMax = pr.getInt("max_${s.inicio}", 0)
        }
        val hr = Pulsometro.bpm.value
        if (hr > 0) {
            gymKcal += Kcal.porMinuto(hr) / 60.0
            gymSuma += hr; gymN++; if (hr > gymMax) gymMax = hr
        }
        if (tickN % 10 == 0L) {
            Pulso.publicar(hr, gymKcal.toInt())
            getSharedPreferences("gym", MODE_PRIVATE).edit().putFloat("kcal_${s.inicio}", gymKcal.toFloat())
                .putLong("suma_${s.inicio}", gymSuma).putLong("n_${s.inicio}", gymN).putInt("max_${s.inicio}", gymMax).apply()
            // Para las estadísticas de día, semana y mes.
            Gym.registrar(s, gymKcal.toInt(), if (gymN > 0) (gymSuma / gymN).toInt() else null, gymMax.takeIf { it > 0 }, false)
        }
        val t = s.timer
        val texto = if (t != null && !t.terminado(now)) {
            (if (t.tipo == "trabajo") "Serie" else "Descanso") + " " + Fechas.mmss(t.restanteMs(now) / 1000)
        } else "Sesión " + Fechas.hms(s.duracionMs(now) / 1000)
        if (tickN % 5 == 0L || texto != notifTexto) actualizar("Gym · " + (Rutina.dia(s.dia ?: "")?.nombre ?: ""), texto)
        if (t != null && t.terminado(now) && avisadoFin != t.fin) {
            avisadoFin = t.fin
            Avisos.vibrar(this, Avisos.FIN_DESCANSO)
        }
    }

    /** Manda el pulso de la muñeca al móvil cada segundo. Se para si el móvil deja de pedirlo. */
    private fun tickPulso(now: Long) {
        if (now - pingMovil > 30_000) { pararPulso(); return }
        val hr = Pulsometro.bpm.value
        if (hr > 0) com.bandeja.shared.Sync.enviar("/hr", hr.toString())
        if (!gymActivo && grab == null && tickN % 5 == 0L) actualizar("Bici en el móvil", "Mandando pulso ♥ ${if (hr > 0) hr else "--"}")
    }

    private fun pararPulso() {
        if (!pulsoActivo) return
        pulsoActivo = false
        Pulsometro.parar()
    }

    private fun parar() {
        main.removeCallbacks(bucle)
        wake?.let { if (it.isHeld) it.release() }
        wake = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notificacion(titulo: String, texto: String): Notification {
        val abrir = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(abrir)
            .setCategory(Notification.CATEGORY_WORKOUT)
            .build()
    }

    private fun actualizar(titulo: String, texto: String) {
        notifTexto = texto
        getSystemService(NotificationManager::class.java).notify(ID, notificacion(titulo, texto))
    }

    private fun enPrimerPlano(texto: String) {
        val n = notificacion("Entrenar y pádel", texto)
        var tipos = 0
        if (tiene(Manifest.permission.BODY_SENSORS)) tipos = tipos or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        if (tiene(Manifest.permission.BLUETOOTH_CONNECT)) tipos = tipos or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (tiene(Manifest.permission.ACCESS_FINE_LOCATION)) tipos = tipos or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        runCatching { startForeground(ID, n, tipos) }
            .recoverCatching { startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH) }
            .recoverCatching { startForeground(ID, n) }
    }

    private fun tiene(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        grab?.terminar()
        grab = null
        _bici.value = false
        if (gymActivo) Pulsometro.parar()
        if (pulsoActivo) { pulsoActivo = false; Pulsometro.parar() }
        main.removeCallbacksAndMessages(null)
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    /** Una sesión de bici en marcha. */
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
        // Siempre existe; se enciende/apaga y ajusta en plena bici (también desde el móvil).
        // Empieza en pausa: se arranca con el botón ⚡ del reloj o «Empezar cambios» del móvil.
        val ritmo = Bici.Ritmo(aj.n, aj.lo, aj.hi).apply { activo = false; Bici.guardar(aj.copy(marcha = false)) }
        var polar: PolarBle? = null
        var lm: LocationManager? = null
        var ultimoSeg = -1L

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
            Pulsometro.empezar(this@EntrenoService)
            if (conPolar) {
                polar = PolarBle(this@EntrenoService, onPulso = { b ->
                    hrPolar = b
                    ultimoPolar = System.currentTimeMillis()
                }, onEstado = { _polar.value = it }).also { it.empezar() }
            }
            if (aj.gps && tiene(Manifest.permission.ACCESS_FINE_LOCATION)) {
                lm = getSystemService(LocationManager::class.java)
                runCatching { lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gps, Looper.getMainLooper()) }
            }
            publicar(System.currentTimeMillis())
        }

        fun seg(now: Long) = (acumulado + if (!pausa) now - desde else 0) / 1000

        fun pausar() {
            val now = System.currentTimeMillis()
            if (pausa) { pausa = false; desde = now } else { acumulado += now - desde; pausa = true }
            publicar(now)
        }

        fun tick(now: Long) {
            // Pulso: el del Polar si está llegando; si no, el de la muñeca.
            val polarVivo = polar != null && now - ultimoPolar < 5000
            if (polar != null) {
                val nueva = if (polarVivo) "polar" else if (now - inicio > 15_000) "reloj" else fuente
                if (nueva != fuente) {
                    fuente = nueva
                    Avisos.vibrar(this@EntrenoService, longArrayOf(0, 120, 80, 120))
                }
            }
            val hr = if (polarVivo) hrPolar else Pulsometro.bpm.value
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
                        "start" -> { Avisos.vibrar(this@EntrenoService, Avisos.START) }
                        "break" -> { Avisos.vibrar(this@EntrenoService, Avisos.BREAK) }
                        "finish" -> { Avisos.vibrar(this@EntrenoService, Avisos.FINISH) }
                    }
                    publicar(now, hr)
                }
            }
            _vivo.value = json(now, hr)
            if (tickN % 2 == 0L) publicar(now, hr)
            if (tickN % 5 == 0L) actualizar(if (aj.gps) "Bici · exterior" else "Bici · spinning",
                "♥ ${if (hr > 0) hr else "--"} · ${Fechas.hms(s)}" + if (pausa) " · en pausa" else "")
        }

        fun publicar(now: Long, hr: Int = 0) {
            val j = json(now, hr)
            _vivo.value = j
            Docs.put(Bici.LIVE, j)
        }

        fun json(now: Long, hr: Int): JSONObject = JSONObject().apply {
                put("activa", true); put("pausa", pausa); put("inicio", inicio); put("seg", seg(now))
                put("hr", hr); put("fuente", fuente)
                put("media", if (n > 0) (suma / n).toInt() else 0); put("max", max); put("kcal", kcal.toInt())
                put("km", if (aj.gps) km else JSONObject.NULL); put("kmh", if (aj.gps) (kmh ?: 0.0) else JSONObject.NULL)
                put("ritmo", ritmo.json(now))
                put("hrs", JSONArray(hrs.toList()))
                put("t", now)
            }

        fun terminar() {
            val now = System.currentTimeMillis()
            polar?.parar()
            runCatching { lm?.removeUpdates(gps) }
            Pulsometro.parar()
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
            _vivo.value = null
        }
    }
}
