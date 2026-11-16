package com.bandeja.padel

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Muestras de pulso del partido actual: "segundo,bpm" por línea en un archivo interno. */
class HrLog(context: Context, fileName: String = "pulso_partido.csv") {
    private val file = File(context.filesDir, fileName)
    private var lastSecond = -1L

    fun clear() {
        file.delete()
        lastSecond = -1L
    }

    /** Guarda una muestra como mucho cada [everySeconds] segundos de partido. */
    fun add(second: Long, bpm: Int, everySeconds: Int = 10): Boolean {
        if (lastSecond < 0) lastSecond = samples().lastOrNull()?.first ?: -everySeconds.toLong()
        if (second - lastSecond < everySeconds) return false
        lastSecond = second
        runCatching { file.appendText("$second,$bpm\n") }
        return true
    }

    fun samples(): List<Pair<Long, Int>> = runCatching {
        file.readLines().mapNotNull { line ->
            val p = line.split(',')
            val s = p.getOrNull(0)?.toLongOrNull()
            val b = p.getOrNull(1)?.toIntOrNull()
            if (s != null && b != null) s to b else null
        }
    }.getOrDefault(emptyList())
}

/** Calorías estimadas con la fórmula de Keytel (hombre) a partir del pulso de cada momento. */
object Calories {
    fun perMinute(bpm: Int, weightKg: Float, age: Int): Double =
        ((-55.0969 + 0.6309 * bpm + 0.1988 * weightKg + 0.2017 * age) / 4.184).coerceAtLeast(0.0)

    /** Cada muestra cuenta hasta la siguiente (máximo 60 s); la última, 10 s. */
    fun total(samples: List<Pair<Long, Int>>, weightKg: Float, age: Int): Int {
        var k = 0.0
        samples.forEachIndexed { i, (s, b) ->
            val next = samples.getOrNull(i + 1)?.first ?: (s + 10)
            k += perMinute(b, weightKg, age) * (next - s).coerceIn(0, 60) / 60.0
        }
        return k.toInt()
    }

    fun age(birthYear: Int, atMs: Long): Int {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = atMs }
        return c.get(java.util.Calendar.YEAR) - birthYear
    }
}

/** Crea el resumen del partido en JSON y CSV y lo guarda en Descargas/Bandeja del reloj. */
object MatchExport {
    private fun teamKey(t: Team) = if (t == Team.A) "azul" else "verde"

    fun json(saved: Saved, samples: List<Pair<Long, Int>>, now: Long): JSONObject {
        val st = saved.state()
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = if (saved.matchStartedAt > 0) saved.matchStartedAt else now
        val durationSec = saved.elapsedMs(now) / 1000

        // Sets: los terminados y, si el partido no ha acabado, el que está en juego.
        val setsJson = JSONArray()
        st.sets.forEach { s ->
            setsJson.put(JSONObject().apply {
                put("azul", s.a); put("verde", s.b)
                put("tipo", if (s.superTieBreak) "super_tiebreak" else if (maxOf(s.a, s.b) == 7 && minOf(s.a, s.b) == 6) "tiebreak" else "normal")
            })
        }
        if (!st.isFinished && (st.gamesA > 0 || st.gamesB > 0 || st.pointsA > 0 || st.pointsB > 0)) {
            setsJson.put(JSONObject().apply {
                put("azul", st.gamesA); put("verde", st.gamesB); put("tipo", "en_juego")
            })
        }
        val marcador = buildList {
            st.sets.forEach { add(if (it.superTieBreak) "[${it.a}-${it.b}]" else "${it.a}-${it.b}") }
            if (!st.isFinished && (st.gamesA > 0 || st.gamesB > 0)) add("${st.gamesA}-${st.gamesB}")
        }.joinToString(" ")

        val bpms = samples.map { it.second }
        val pulso = JSONObject().apply {
            if (bpms.isNotEmpty()) {
                put("media", bpms.average().toInt())
                put("maxima", bpms.max())
                put("minima", bpms.min())
            } else {
                put("media", JSONObject.NULL); put("maxima", JSONObject.NULL); put("minima", JSONObject.NULL)
            }
            put("calorias", Calories.total(samples, saved.weightKg, Calories.age(saved.birthYear, start)))
            put("intervalo_segundos", 10)
            put("muestras", JSONArray().apply {
                samples.forEach { (s, b) -> put(JSONObject().apply { put("segundo", s); put("bpm", b) }) }
            })
        }

        return JSONObject().apply {
            put("app", "Bandeja")
            put("formato", 1)
            put("fecha", day.format(Date(start)))
            put("inicio", iso.format(Date(start)))
            put("exportado", iso.format(Date(now)))
            put("duracion_segundos", durationSec)
            put("duracion", formatHms(durationSec))
            put("terminado", st.isFinished)
            put("ganador", st.winner?.let { teamKey(it) } ?: JSONObject.NULL)
            put("marcador", marcador)
            put("sets_ganados", JSONObject().apply {
                put("azul", st.setsWon(Team.A)); put("verde", st.setsWon(Team.B))
            })
            put("sets", setsJson)
            put("puntos_ganados", JSONObject().apply {
                put("azul", saved.history.count { it == Team.A })
                put("verde", saved.history.count { it == Team.B })
            })
            put("reglas", JSONObject().apply {
                put("cuarenta_iguales", when (saved.rules.deuceMode) {
                    DeuceMode.GOLDEN -> "punto_de_oro"
                    DeuceMode.ADVANTAGE -> "ventajas"
                    DeuceMode.STAR_POINT -> "star_point"
                })
                put("sets_para_ganar", saved.rules.setsToWin)
                put("super_tiebreak", saved.rules.superTieBreak)
            })
            put("pulsaciones", pulso)
        }
    }

    /** CSV de una sola fila con el resumen (fácil de abrir en Excel o importar). */
    fun csv(j: JSONObject): String {
        val p = j.getJSONObject("pulsaciones")
        val header = "fecha;inicio;duracion_segundos;duracion;marcador;ganador;sets_azul;sets_verde;puntos_azul;puntos_verde;pulso_medio;pulso_maximo;pulso_minimo;calorias"
        val row = listOf(
            j.getString("fecha"), j.getString("inicio"), j.getLong("duracion_segundos").toString(),
            j.getString("duracion"), j.getString("marcador"), j.opt("ganador")?.takeIf { it != JSONObject.NULL }?.toString() ?: "",
            j.getJSONObject("sets_ganados").getInt("azul").toString(), j.getJSONObject("sets_ganados").getInt("verde").toString(),
            j.getJSONObject("puntos_ganados").getInt("azul").toString(), j.getJSONObject("puntos_ganados").getInt("verde").toString(),
            p.opt("media")?.takeIf { it != JSONObject.NULL }?.toString() ?: "",
            p.opt("maxima")?.takeIf { it != JSONObject.NULL }?.toString() ?: "",
            p.opt("minima")?.takeIf { it != JSONObject.NULL }?.toString() ?: "",
            p.optInt("calorias", 0).toString(),
        ).joinToString(";")
        return "$header\n$row\n"
    }

    /** Guarda los dos archivos en Descargas/Bandeja. Devuelve el nombre base o null si falla. */
    fun save(context: Context, saved: Saved, samples: List<Pair<Long, Int>>): String? = runCatching {
        val now = System.currentTimeMillis()
        val j = json(saved, samples, now)
        val start = if (saved.matchStartedAt > 0) saved.matchStartedAt else now
        val base = "partido-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(start))
        write(context, "$base.json", "application/json", j.toString(2))
        write(context, "$base.csv", "text/csv", csv(j))
        base
    }.getOrNull()

    internal fun write(context: Context, name: String, mime: String, text: String) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // Si ya existe (se exportó antes el mismo partido) se sustituye.
        resolver.delete(
            collection,
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, "Download/Bandeja/"),
        )
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Bandeja")
        }
        val uri = resolver.insert(collection, values) ?: error("No se pudo crear $name")
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }

    internal fun formatHms(sec: Long) = "%02d:%02d:%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)
}

/** Resumen de la sesión de bici en JSON y CSV (mismo formato que el pádel, sin marcador). */
object BikeExport {
    fun json(saved: Saved, samples: List<Pair<Long, Int>>, now: Long): JSONObject {
        val b = saved.bike
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = if (b.started) b.startedAt else now
        val durationSec = b.elapsedMs(now) / 1000
        val bpms = samples.map { it.second }
        val pulso = JSONObject().apply {
            if (bpms.isNotEmpty()) {
                put("media", bpms.average().toInt())
                put("maxima", bpms.max())
                put("minima", bpms.min())
            } else {
                put("media", JSONObject.NULL); put("maxima", JSONObject.NULL); put("minima", JSONObject.NULL)
            }
            put("calorias", Calories.total(samples, saved.weightKg, Calories.age(saved.birthYear, start)))
            put("intervalo_segundos", 10)
            put("muestras", JSONArray().apply {
                samples.forEach { (s, bpm) -> put(JSONObject().apply { put("segundo", s); put("bpm", bpm) }) }
            })
        }
        return JSONObject().apply {
            put("app", "Bandeja")
            put("formato", 1)
            put("tipo", "bici")
            put("fecha", day.format(Date(start)))
            put("inicio", iso.format(Date(start)))
            put("exportado", iso.format(Date(now)))
            put("duracion_segundos", durationSec)
            put("duracion", MatchExport.formatHms(durationSec))
            put("terminado", b.finished)
            put("peso_kg", saved.weightKg.toDouble())
            put("pulsaciones", pulso)
        }
    }

    fun csv(j: JSONObject): String {
        val p = j.getJSONObject("pulsaciones")
        fun opt(k: String) = p.opt(k)?.takeIf { it != JSONObject.NULL }?.toString() ?: ""
        val header = "fecha;inicio;duracion_segundos;duracion;pulso_medio;pulso_maximo;pulso_minimo;calorias"
        val row = listOf(
            j.getString("fecha"), j.getString("inicio"), j.getLong("duracion_segundos").toString(),
            j.getString("duracion"), opt("media"), opt("maxima"), opt("minima"), p.optInt("calorias", 0).toString(),
        ).joinToString(";")
        return "$header\n$row\n"
    }

    fun save(context: Context, saved: Saved, samples: List<Pair<Long, Int>>): String? = runCatching {
        val now = System.currentTimeMillis()
        val j = json(saved, samples, now)
        val start = if (saved.bike.started) saved.bike.startedAt else now
        val base = "bici-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(start))
        MatchExport.write(context, "$base.json", "application/json", j.toString(2))
        MatchExport.write(context, "$base.csv", "text/csv", csv(j))
        base
    }.getOrNull()
}
