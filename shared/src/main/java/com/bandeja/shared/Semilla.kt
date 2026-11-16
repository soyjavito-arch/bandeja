package com.bandeja.shared

import android.content.Context
import org.json.JSONObject

/** Datos que ya había en la web (series y pesos), se cargan una vez como si fueran muy antiguos. */
object Semilla {
    fun cargar(context: Context) {
        if (Docs.get("_semilla") != null) return
        runCatching {
            val o = JSONObject(context.assets.open("semilla.json").bufferedReader().readText())
            o.keys().forEach { k ->
                val raw = JSONObject().apply { put("t", 1L); put("v", o.getJSONObject(k)) }.toString()
                Docs.merge(k, raw)
            }
        }
        Docs.put("_semilla", JSONObject().apply { put("v", 1) })
    }
}
