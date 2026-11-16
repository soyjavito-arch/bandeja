package com.bandeja.shared

import org.json.JSONArray
import org.json.JSONObject

/**
 * Partidos guardados: "padel-AAAA-MM-DD-HHMM" con el mismo formato que la web
 * {fecha, inicio, dur, ganador, terminado, marcador, sets:[[azul,verde,tipo]], setsG, ptos, pm, pmax, pmin, kcal, hr:[[seg,bpm]], reglas, nota, unSet}.
 */
object Partidos {
    fun clave(inicioMs: Long) = "padel-" + Fechas.clave(inicioMs)

    /** Convierte el JSON que exporta el marcador en el documento del historial. */
    fun desdeExport(j: JSONObject, unSet: Boolean): Pair<String, JSONObject> {
        val inicio = Fechas.desdeIso(j.optString("inicio")) ?: System.currentTimeMillis()
        val p = j.optJSONObject("pulsaciones") ?: JSONObject()
        val sg = j.optJSONObject("sets_ganados") ?: JSONObject()
        val pg = j.optJSONObject("puntos_ganados") ?: JSONObject()
        val sets = JSONArray()
        j.optJSONArray("sets")?.let { a ->
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: continue
                sets.put(JSONArray().apply { put(s.optInt("azul")); put(s.optInt("verde")); put(s.optString("tipo", "normal")) })
            }
        }
        val hr = JSONArray()
        p.optJSONArray("muestras")?.let { a ->
            for (i in 0 until a.length()) {
                val m = a.optJSONObject(i) ?: continue
                hr.put(JSONArray().apply { put(m.optLong("segundo")); put(m.optInt("bpm")) })
            }
        }
        val doc = JSONObject().apply {
            put("fecha", j.optString("fecha"))
            put("inicio", j.optString("inicio"))
            put("dur", j.optLong("duracion_segundos"))
            put("ganador", j.opt("ganador") ?: JSONObject.NULL)
            put("terminado", j.optBoolean("terminado", true))
            put("marcador", j.optString("marcador"))
            put("sets", sets)
            put("setsG", JSONArray().apply { put(sg.optInt("azul")); put(sg.optInt("verde")) })
            put("ptos", JSONArray().apply { put(pg.optInt("azul")); put(pg.optInt("verde")) })
            put("pm", p.opt("media") ?: JSONObject.NULL)
            put("pmax", p.opt("maxima") ?: JSONObject.NULL)
            put("pmin", p.opt("minima") ?: JSONObject.NULL)
            put("kcal", p.opt("calorias") ?: JSONObject.NULL)
            put("hr", hr)
            put("reglas", j.optJSONObject("reglas")?.optString("cuarenta_iguales") ?: JSONObject.NULL)
            put("unSet", unSet)
            put("src", "reloj")
        }
        return clave(inicio) to doc
    }

    data class Resumen(
        val key: String,
        val inicio: Long,
        val sets: List<Triple<Int, Int, String>>,
        val ganador: String?,
        val terminado: Boolean,
        val dur: Long,
        val pm: Int?,
        val kcal: Int?,
        val unSet: Boolean,
        val nota: String,
    )

    fun leer(key: String): Resumen? {
        val d = Docs.get(key) ?: return null
        val inicio = Fechas.desdeIso(d.optString("inicio")) ?: inicioDeClave(key)
        val a = d.optJSONArray("sets") ?: JSONArray()
        val sets = (0 until a.length()).mapNotNull { i ->
            a.optJSONArray(i)?.let { Triple(it.optInt(0), it.optInt(1), it.optString(2, "normal")) }
        }
        return Resumen(key, inicio, sets, d.optString("ganador").takeIf { it == "azul" || it == "verde" },
            d.optBoolean("terminado", true), d.optLong("dur"),
            if (d.isNull("pm")) null else d.optInt("pm"), if (d.isNull("kcal")) null else d.optInt("kcal"),
            d.optBoolean("unSet", sets.size == 1 && d.optBoolean("terminado", true)), d.optString("nota"))
    }

    fun inicioDeClave(key: String): Long = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.US).parse(key.substringAfter('-'))!!.time
    }.getOrDefault(0L)

    fun todos(): List<Resumen> = Docs.keys("padel-").mapNotNull { leer(it) }.sortedByDescending { it.inicio }

    /** Guarda los cambios hechos a mano en el móvil. */
    fun editar(key: String, sets: List<Pair<Int, Int>>, nota: String, unSet: Boolean, nuevaFecha: Long?) {
        val d = Docs.get(key) ?: return
        val arr = JSONArray()
        var ga = 0; var gb = 0
        sets.forEach { (a, b) ->
            arr.put(JSONArray().apply { put(a); put(b); put("normal") })
            if (a > b) ga++ else if (b > a) gb++
        }
        d.put("sets", arr)
        d.put("setsG", JSONArray().apply { put(ga); put(gb) })
        d.put("marcador", sets.joinToString(" ") { "${it.first}-${it.second}" })
        d.put("ganador", if (ga > gb) "azul" else if (gb > ga) "verde" else JSONObject.NULL)
        d.put("nota", nota)
        d.put("unSet", unSet)
        if (nuevaFecha != null && clave(nuevaFecha) != key) {
            d.put("inicio", Fechas.iso(nuevaFecha))
            d.put("fecha", Fechas.dia(nuevaFecha))
            Docs.delete(key)
            Docs.put(clave(nuevaFecha), d)
        } else Docs.put(key, d)
    }
}
