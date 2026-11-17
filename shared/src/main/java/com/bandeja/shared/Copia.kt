package com.bandeja.shared

import org.json.JSONObject

/** Copia de seguridad de todos los datos (series, pesos, sesiones, partidos y bici) en un archivo JSON. */
object Copia {
    fun crear(): String = JSONObject().apply {
        put("app", "Entrenar y pádel")
        put("formato", 1)
        put("fecha", Fechas.iso(System.currentTimeMillis()))
        put("docs", JSONObject().apply { Docs.allRaw().forEach { (k, v) -> put(k, v) } })
    }.toString()

    /** Devuelve cuántos datos se han recuperado (solo se sustituye lo que en la copia es más nuevo). */
    fun restaurar(texto: String): Int {
        val docs = JSONObject(texto).getJSONObject("docs")
        var n = 0
        docs.keys().forEach { k -> if (Docs.importar(k, docs.getString(k))) n++ }
        return n
    }

    fun nombre(): String = "entrenar-y-padel-copia-" + Fechas.clave(System.currentTimeMillis()) + ".json"
}
