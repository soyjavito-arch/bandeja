package com.bandeja.shared

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Almacén de documentos clave → JSON, con el mismo formato que la base de datos de la web
 * ("martes-banca" → {hist:[...]}, "_sesiones", "padel-AAAA-MM-DD-HHMM", "bici-…").
 *
 * Cada documento guarda la hora del último cambio. Al sincronizar con el otro dispositivo
 * gana el cambio más reciente. Un documento borrado se guarda como marca de borrado
 * para que el borrado también llegue al otro lado.
 */
object Docs {
    private lateinit var prefs: SharedPreferences
    private val cache = HashMap<String, Entry>()

    /** Cambia cada vez que se modifica algo: la interfaz lo observa para repintarse. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    /** Se llama al guardar un cambio local, para mandarlo al otro dispositivo. */
    var onLocalChange: ((key: String, raw: String) -> Unit)? = null

    data class Entry(val t: Long, val v: JSONObject?) {
        fun raw(): String = JSONObject().apply {
            put("t", t)
            put("v", v ?: JSONObject.NULL)
        }.toString()

        companion object {
            fun parse(raw: String?): Entry? = runCatching {
                val o = JSONObject(raw ?: return null)
                Entry(o.optLong("t"), o.optJSONObject("v"))
            }.getOrNull()
        }
    }

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("docs", Context.MODE_PRIVATE)
        prefs.all.forEach { (k, v) -> Entry.parse(v as? String)?.let { cache[k] = it } }
    }

    @Synchronized
    fun get(key: String): JSONObject? = cache[key]?.v?.let { JSONObject(it.toString()) }

    @Synchronized
    fun keys(prefix: String = ""): List<String> =
        cache.filter { it.key.startsWith(prefix) && it.value.v != null }.keys.sorted()

    fun put(key: String, value: JSONObject) = write(key, Entry(System.currentTimeMillis(), value), true)

    fun delete(key: String) = write(key, Entry(System.currentTimeMillis(), null), true)

    /** Documentos que se juntan campo a campo en vez de quedarse con el más nuevo (la sesión de gym). */
    val mezcladores = HashMap<String, (JSONObject, Long, JSONObject, Long) -> JSONObject>()

    /** Cambio que llega del otro dispositivo: solo se aplica si es más nuevo (o se junta, si tiene mezclador). */
    fun merge(key: String, raw: String): Boolean {
        val e = Entry.parse(raw) ?: return false
        val mezclar = mezcladores[key]
        if (mezclar != null) {
            val cur = synchronized(this) { cache[key] }
            if (cur?.v != null && e.v != null) {
                if (cur.t == e.t && cur.v.toString() == e.v.toString()) return false
                val m = mezclar(JSONObject(cur.v.toString()), cur.t, JSONObject(e.v.toString()), e.t)
                val t = maxOf(cur.t, e.t)
                return when (m.toString()) {
                    // Lo suyo ya incluye lo nuestro: nos lo quedamos (si su hora va atrasada, con hora nueva y avisando).
                    e.v.toString() -> { if (e.t > cur.t) write(key, e, false) else write(key, Entry(cur.t + 1, m), true); true }
                    // Lo nuestro ya incluye lo suyo: si el suyo era más nuevo, se le vuelve a mandar lo nuestro.
                    cur.v.toString() -> { if (e.t > cur.t) write(key, Entry(t + 1, m), true); e.t > cur.t }
                    // Cada uno tenía algo: se juntan, se guarda y se manda.
                    else -> { write(key, Entry(t + 1, m), true); true }
                }
            }
        }
        synchronized(this) {
            val cur = cache[key]
            if (cur != null && cur.t >= e.t) return false
        }
        write(key, e, false)
        return true
    }

    /** Copia de seguridad: lo que traiga y sea más nuevo se guarda y se manda también al otro aparato. */
    fun importar(key: String, raw: String): Boolean {
        val e = Entry.parse(raw) ?: return false
        synchronized(this) {
            val cur = cache[key]
            if (cur != null && cur.t >= e.t) return false
        }
        write(key, e, true)
        return true
    }

    fun raw(key: String): String? = synchronized(this) { cache[key]?.raw() }

    fun allRaw(): Map<String, String> = synchronized(this) { cache.mapValues { it.value.raw() } }

    private fun write(key: String, e: Entry, local: Boolean) {
        val raw = e.raw()
        synchronized(this) {
            cache[key] = e
            prefs.edit().putString(key, raw).apply()
        }
        _version.value = _version.value + 1
        if (local) onLocalChange?.invoke(key, raw)
    }
}
