package com.bandeja.shared

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Un día de gym de la rutina (martes, jueves o viernes). */
data class Dia(
    val id: String,
    val nombre: String,
    val tag: String,
    val sub: String,
    val nota: String,
    val ejercicios: List<Ejercicio>,
)

/**
 * Un ejercicio tal cual está en la web:
 * nombre, series ("4x8-10", "3x1 min", "5 min"), descanso ("2 min", "90 s", "—"),
 * consejo, búsqueda de vídeos y clave del GIF.
 */
data class Ejercicio(
    val nombre: String,
    val series: String,
    val descanso: String,
    val consejo: String,
    val busqueda: String,
    val clave: String,
) {
    /** Segundos de descanso entre series (0 si no hay). */
    val descansoSeg: Int get() = segundos(descanso)

    /** Segundos de trabajo de cada serie si es por tiempo (plancha, remo, bici), si no 0. */
    val trabajoSeg: Int
        get() = when (clave) {
            "plancha", "planchaLat" -> segundos(series.replace(Regex("^\\d+x"), ""))
            "bici", "warm" -> segundos(series)
            else -> 0
        }

    val porTiempo: Boolean get() = trabajoSeg > 0

    /** Número de series que se apuntan (0 = estiramientos, sin registro). */
    val numSeries: Int
        get() {
            val m = Regex("^(\\d+)x").find(series)
            return if (m != null && (!Regex("seg|min").containsMatchIn(series) || porTiempo)) m.groupValues[1].toInt()
            else if (porTiempo) 1 else 0
        }

    /** Las series llevan reps y kg (no son por tiempo ni estiramientos). */
    val conPeso: Boolean get() = numSeries > 0 && !porTiempo

    val gif: String get() = "gif/$clave.gif"

    companion object {
        fun segundos(t: String): Int {
            val m = Regex("(\\d+)\\s*(min|s)").find(t) ?: return 0
            val n = m.groupValues[1].toInt()
            // "1.30 min" de los estiramientos no cuenta como descanso
            return if (m.groupValues[2] == "min") n * 60 else n
        }
    }
}

/** Pasos de kilos igual que en la web: de 0,5 en 0,5 hasta 60 y de 2,5 en 2,5 hasta 200. */
object Kilos {
    val pasos: List<Double> = buildList {
        var k = 0.0
        while (k <= 60.0) { add(k); k += 0.5 }
        k = 62.5
        while (k <= 200.0) { add(k); k += 2.5 }
    }

    fun subir(kg: Double?): Double = pasos.firstOrNull { it > (kg ?: -1.0) + 1e-6 } ?: pasos.last()
    fun bajar(kg: Double?): Double = pasos.lastOrNull { it < (kg ?: 0.5) - 1e-6 } ?: 0.0

    fun texto(kg: Double?): String {
        if (kg == null) return "—"
        val s = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()
        return s.replace('.', ',')
    }
}

object Fechas {
    private val dia = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val clave = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US)
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    fun hoy(): String = dia.format(Date())
    fun dia(ms: Long): String = dia.format(Date(ms))
    fun clave(ms: Long): String = clave.format(Date(ms))
    fun iso(ms: Long): String = iso.format(Date(ms))
    fun desdeIso(s: String?): Long? = runCatching { iso.parse(s ?: "")?.time }.getOrNull()

    /** Id del día de gym de hoy (martes/jueves/viernes) o null. */
    fun diaGymHoy(): String? = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.TUESDAY -> "martes"
        Calendar.THURSDAY -> "jueves"
        Calendar.FRIDAY -> "viernes"
        else -> null
    }

    fun mmss(seg: Long): String {
        val s = seg.coerceAtLeast(0)
        return "%d:%02d".format(s / 60, s % 60)
    }

    fun hms(seg: Long): String {
        val s = seg.coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else mmss(s)
    }

    private val nombreDia = SimpleDateFormat("EEE d MMM", Locale("es", "ES"))
    private val hora = SimpleDateFormat("HH:mm", Locale.US)
    fun corto(ms: Long): String = nombreDia.format(Date(ms))
    fun hora(ms: Long): String = hora.format(Date(ms))
}

/** Calorías con Keytel (hombre) usando su VO2máx de Polar (49). */
object Kcal {
    const val PESO = 83.65
    const val NACIDO = 1979
    const val VO2MAX = 49.0

    fun porMinuto(bpm: Int, pesoKg: Double = PESO, edad: Int = edad()): Double =
        ((-95.7735 + 0.634 * bpm + 0.404 * VO2MAX + 0.394 * pesoKg + 0.271 * edad) / 4.184).coerceAtLeast(0.0)

    fun edad(): Int = Calendar.getInstance().get(Calendar.YEAR) - NACIDO
}

/** Zonas de pulso de Javi, las mismas que le da Polar (máx. 175). */
object Zonas {
    /** Pulso donde empieza cada zona (datos de Javi del 17/11/2026): Z1 112, Z2 125, Z3 138, Z4 150, Z5 162; 6 = máximo (175). */
    private val LIMITES = intArrayOf(112, 125, 138, 150, 162, 175)

    fun max(): Int = LIMITES[5]

    /** 1..5 (0 si está por debajo de la zona 1). */
    fun zona(bpm: Int): Int {
        if (bpm < LIMITES[0]) return 0
        for (z in 5 downTo 1) if (bpm >= LIMITES[z - 1]) return z
        return 0
    }

    fun desde(z: Int): Int = LIMITES[(z - 1).coerceIn(0, 5)]

    fun nombre(z: Int): String = when (z) {
        1 -> "Zona 1 · recuperación"
        2 -> "Zona 2 · base aeróbica"
        3 -> "Zona 3 · tempo"
        4 -> "Zona 4 · umbral"
        5 -> "Zona 5 · VO2 máx"
        else -> "Reposo"
    }
}
