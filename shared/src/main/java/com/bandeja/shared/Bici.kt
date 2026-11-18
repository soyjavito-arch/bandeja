package com.bandeja.shared

import org.json.JSONArray
import org.json.JSONObject

/**
 * Bici (spinning o exterior). La sesión la graba el reloj, que lee el Polar H10 por Bluetooth
 * (o su propio pulso si no está el Polar). El móvil la ve en directo y puede mandar órdenes.
 *
 * - "_bici_cfg": ajustes {gps, cambios:{on,n,lo,hi}}
 * - "_bici": sesión en marcha (la escribe el reloj)
 * - "bici-AAAA-MM-DD-HHMM": sesión guardada (mismo formato que la web + "cambios")
 */
object Bici {
    const val CFG = "_bici_cfg"
    const val LIVE = "_bici"
    const val CMD = "/bici"
    const val POLAR_ID = "E2649629"

    /** marcha: los cambios de ritmo están en marcha (botón Empezar/Pausar); al empezar una bici se ponen en pausa. */
    data class Ajustes(val gps: Boolean = false, val on: Boolean = false, val n: Int = 6, val lo: Int = 120, val hi: Int = 165,
                       val marcha: Boolean = false)

    fun ajustes(): Ajustes {
        val o = Docs.get(CFG) ?: return Ajustes()
        val c = o.optJSONObject("cambios") ?: JSONObject()
        return Ajustes(o.optBoolean("gps"), c.optBoolean("on"), c.optInt("n", 6).coerceIn(1, 30),
            c.optInt("lo", 120), c.optInt("hi", 165), c.optBoolean("marcha"))
    }

    fun guardar(a: Ajustes) {
        Docs.put(CFG, JSONObject().apply {
            put("gps", a.gps)
            put("cambios", JSONObject().apply { put("on", a.on); put("n", a.n); put("lo", a.lo); put("hi", a.hi); put("marcha", a.marcha) })
        })
    }

    /** Un cambio de ritmo: lo que tardas en subir de lo a hi y lo que tardas en recuperar. */
    data class Cambio(val subidaMs: Long, val recMs: Long)

    /**
     * Cambios de ritmo por pulso:
     * esperando → (pulso ≤ lo) «Start» → subiendo → (pulso ≥ hi) «Break» → recuperando → (≤ lo) «Start»…
     * En el último cambio dice «Finish» en vez de «Break».
     */
    class Ritmo(var n: Int, var lo: Int, var hi: Int) {
        /** Se puede encender, apagar y cambiar en plena bici. */
        var activo = true
        var estado = "esperando" // esperando, subiendo, recuperando, fin
        var num = 0 // cambio en curso (1..n)
        var desde = 0L
        var finRec = 0L
        val hechos = ArrayList<Cambio>()

        /** Devuelve "start", "break", "finish" o null. */
        private var pausaIni = 0L

        /** Activo = encendido y en marcha. Al pausar en plena subida el cronómetro se congela y luego sigue. */
        fun ajustar(a: Ajustes) {
            val nuevo = a.on && a.marcha
            val now = System.currentTimeMillis()
            if (activo && !nuevo) pausaIni = now
            if (!activo && nuevo && pausaIni > 0) {
                if (estado == "subiendo") desde += now - pausaIni
                if (estado == "recuperando") finRec += now - pausaIni
                pausaIni = 0
            }
            activo = nuevo
            n = a.n; lo = a.lo; hi = a.hi
            if (estado == "fin" && num < n) estado = "recuperando"
        }

        fun pulso(bpm: Int, now: Long): String? {
            if (bpm <= 0 || !activo) return null
            when (estado) {
                "esperando", "recuperando" -> if (bpm <= lo) {
                    if (estado == "recuperando" && hechos.isNotEmpty()) {
                        val l = hechos.removeAt(hechos.size - 1)
                        hechos.add(l.copy(recMs = now - finRec))
                    }
                    num += 1
                    estado = "subiendo"
                    desde = now
                    return "start"
                }
                "subiendo" -> if (bpm >= hi) {
                    hechos.add(Cambio(now - desde, 0))
                    finRec = now
                    return if (num >= n) { estado = "fin"; "finish" } else { estado = "recuperando"; "break" }
                }
            }
            return null
        }

        fun json(now: Long): JSONObject = JSONObject().apply {
            put("estado", estado); put("num", num); put("n", n); put("lo", lo); put("hi", hi); put("activo", activo)
            // Cronómetro del cambio: corre de «Start» a «Break» y se queda parado hasta el siguiente «Start».
            val t = if (!activo && pausaIni > 0) pausaIni else now
            put("ms", if (estado == "subiendo") t - desde else hechos.lastOrNull()?.subidaMs ?: 0)
            put("rec", if (estado == "recuperando") t - finRec else 0)
            put("hechos", JSONArray().apply { hechos.forEach { put(JSONArray().apply { put(it.subidaMs / 1000); put(it.recMs / 1000) }) } })
        }
    }

    /** Lo que el reloj publica mientras graba (para que el móvil lo enseñe). */
    data class Vivo(
        val activa: Boolean,
        val pausa: Boolean,
        val inicio: Long,
        val segundos: Long,
        val hr: Int,
        val fuente: String, // "polar" o "reloj"
        val media: Int,
        val max: Int,
        val kcal: Int,
        val km: Double?,
        val kmh: Double?,
        val ritmo: JSONObject?,
        val hrs: List<Int>,
        val t: Long,
    )

    fun vivo(): Vivo? {
        val o = Docs.get(LIVE) ?: return null
        val a = o.optJSONArray("hrs")
        return Vivo(o.optBoolean("activa"), o.optBoolean("pausa"), o.optLong("inicio"), o.optLong("seg"), o.optInt("hr"),
            o.optString("fuente", "reloj"), o.optInt("media"), o.optInt("max"), o.optInt("kcal"),
            if (o.has("km") && !o.isNull("km")) o.optDouble("km") else null,
            if (o.has("kmh") && !o.isNull("kmh")) o.optDouble("kmh") else null,
            o.optJSONObject("ritmo"), (0 until (a?.length() ?: 0)).map { a!!.optInt(it) }, o.optLong("t"))
    }

    fun mmss(seg: Long) = Fechas.mmss(seg)
}

/** Pulso y calorías que publica el reloj durante el gym (cada pocos segundos). */
object Pulso {
    const val DOC = "_pulso"
    /** src: "reloj" (la app del reloj en Gym) o "movil" (el móvil con el pulso que le manda el reloj en segundo plano). */
    fun publicar(hr: Int, kcal: Int, src: String = "reloj") =
        Docs.put(DOC, JSONObject().apply { put("hr", hr); put("kcal", kcal); put("t", System.currentTimeMillis()); put("src", src) })

    /** El reloj está midiendo el gym con su app abierta (y lo apunta él). */
    fun midiendoReloj(): Boolean {
        val o = Docs.get(DOC) ?: return false
        return System.currentTimeMillis() - o.optLong("t") <= 60_000 && o.optString("src", "reloj") == "reloj"
    }
    fun leer(): Pair<Int, Int>? {
        val o = Docs.get(DOC) ?: return null
        if (System.currentTimeMillis() - o.optLong("t") > 60_000) return null
        return o.optInt("hr") to o.optInt("kcal")
    }
}

/** Marcador de pádel en directo (lo escribe el reloj) y órdenes desde el móvil. */
object PadelVivo {
    const val DOC = "_padel"
    const val CMD = "/padel"
}
