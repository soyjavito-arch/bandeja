package com.bandeja.shared

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lógica del gym compartida por móvil y reloj. Todo se guarda en [Docs]:
 * - "<dia>-<clave>" → {hist:[{d, s:[[reps,kg],…]}]} (igual que la web, últimos 60 días)
 * - "_sesiones" → {martes:[fechas], …}
 * - "_gym" → la sesión de hoy: series hechas, cronómetro y descanso en marcha.
 */
object Gym {
    const val LIVE = "_gym"

    fun clave(dia: String, ej: Ejercicio) = "$dia-${ej.clave}"

    // ---------- Reps y kilos ----------

    /** Valores de cada serie: los últimos apuntados (si no se cambian, se quedan). */
    fun valores(dia: String, ej: Ejercicio): List<Pair<Int?, Double?>> {
        val n = ej.numSeries
        val hist = Docs.get(clave(dia, ej))?.optJSONArray("hist")
        val last = if (hist != null && hist.length() > 0) hist.optJSONObject(hist.length() - 1)?.optJSONArray("s") else null
        val out = ArrayList<Pair<Int?, Double?>>()
        for (i in 0 until n) {
            val row = last?.optJSONArray(minOf(i, (last.length() - 1).coerceAtLeast(0)))
            val r = row?.let { if (it.isNull(0)) null else it.optInt(0) }
            val w = row?.let { if (it.isNull(1)) null else it.optDouble(1) }
            out.add(r to w)
        }
        return out
    }

    /** Valores de la vez anterior a hoy (para el «la semana pasada…»). */
    fun anterior(dia: String, ej: Ejercicio): Pair<String, List<Pair<Int?, Double?>>>? {
        val hist = Docs.get(clave(dia, ej))?.optJSONArray("hist") ?: return null
        val hoy = Fechas.hoy()
        for (i in hist.length() - 1 downTo 0) {
            val h = hist.optJSONObject(i) ?: continue
            if (h.optString("d") == hoy) continue
            val s = h.optJSONArray("s") ?: continue
            return h.optString("d") to (0 until s.length()).map {
                val row = s.optJSONArray(it)
                (if (row == null || row.isNull(0)) null else row.optInt(0)) to (if (row == null || row.isNull(1)) null else row.optDouble(1))
            }
        }
        return null
    }

    fun cambiar(dia: String, ej: Ejercicio, serie: Int, reps: Int?, kg: Double?) {
        val v = valores(dia, ej).toMutableList()
        if (serie !in v.indices) return
        v[serie] = reps to kg
        guardarHoy(dia, ej, v)
    }

    private fun guardarHoy(dia: String, ej: Ejercicio, v: List<Pair<Int?, Double?>>) {
        val key = clave(dia, ej)
        val doc = Docs.get(key) ?: JSONObject()
        val hist = doc.optJSONArray("hist") ?: JSONArray()
        val hoy = Fechas.hoy()
        val s = JSONArray().apply {
            v.forEach { (r, w) -> put(JSONArray().apply { put(r ?: JSONObject.NULL); put(w ?: JSONObject.NULL) }) }
        }
        val list = (0 until hist.length()).mapNotNull { hist.optJSONObject(it) }.toMutableList()
        if (list.isNotEmpty() && list.last().optString("d") == hoy) list.removeAt(list.size - 1)
        list.add(JSONObject().apply { put("d", hoy); put("s", s) })
        doc.put("hist", JSONArray(list.takeLast(60)))
        Docs.put(key, doc)
    }

    // ---------- Sesión de hoy ----------

    data class Temporizador(
        val tipo: String, // "descanso" o "trabajo"
        val key: String,
        val serie: Int,
        val total: Int,
        val fin: Long,
        val pausa: Boolean,
        val quedanMs: Long,
    ) {
        fun restanteMs(now: Long): Long = if (pausa) quedanMs else (fin - now).coerceAtLeast(0)
        fun terminado(now: Long) = !pausa && now >= fin
    }

    data class Sesion(
        val fecha: String,
        val dia: String?,
        val inicio: Long,
        val fin: Long,
        val hechas: Map<String, Set<Int>>,
        val timer: Temporizador?,
        /** Cronómetro de cada ejercicio: clave -> (inicio, fin). fin = 0 mientras sigue. */
        val tiempos: Map<String, Pair<Long, Long>> = emptyMap(),
        /** Pausa de la sesión: cuándo se pausó (0 = corriendo) y ms parados en total (no cuentan). */
        val pausa: Long = 0,
        val parado: Long = 0,
    ) {
        fun tiempoEjMs(key: String, now: Long): Long? = tiempos[key]?.let { (a, b) -> (if (b > 0) b else if (fin > 0 && fin > a) fin else now) - a }
        fun duracionMs(now: Long): Long = when {
            inicio == 0L -> 0
            fin > 0 -> fin - inicio - parado
            pausa > 0 -> pausa - inicio - parado
            else -> now - inicio - parado
        }.coerceAtLeast(0)
        val pausada: Boolean get() = inicio > 0 && fin == 0L && pausa > 0
        fun hechas(key: String): Set<Int> = hechas[key] ?: emptySet()
    }

    fun sesion(): Sesion {
        val o = Docs.get(LIVE)
        val hoy = Fechas.hoy()
        if (o == null || o.optString("d") != hoy) return Sesion(hoy, null, 0, 0, emptyMap(), null)
        val h = HashMap<String, Set<Int>>()
        o.optJSONObject("done")?.let { d ->
            d.keys().forEach { k -> val a = d.optJSONArray(k); h[k] = (0 until (a?.length() ?: 0)).map { a!!.optInt(it) }.toSet() }
        }
        val t = o.optJSONObject("timer")?.let {
            Temporizador(it.optString("tipo"), it.optString("key"), it.optInt("serie"), it.optInt("total"),
                it.optLong("fin"), it.optBoolean("pausa"), it.optLong("quedan"))
        }
        val tiempos = HashMap<String, Pair<Long, Long>>()
        o.optJSONObject("ej")?.let { e ->
            e.keys().forEach { k -> e.optJSONArray(k)?.let { a -> tiempos[k] = a.optLong(0) to a.optLong(1) } }
        }
        return Sesion(hoy, o.optString("dia").ifEmpty { null }, o.optLong("start"), o.optLong("end"), h, t, tiempos,
            o.optLong("pausa"), o.optLong("parado"))
    }

    private fun guardar(s: Sesion) {
        Docs.put(LIVE, JSONObject().apply {
            put("d", s.fecha)
            put("dia", s.dia ?: JSONObject.NULL)
            put("start", s.inicio)
            put("end", s.fin)
            put("pausa", s.pausa)
            put("parado", s.parado)
            put("done", JSONObject().apply { s.hechas.forEach { (k, v) -> put(k, JSONArray(v.sorted())) } })
            put("ej", JSONObject().apply { s.tiempos.forEach { (k, v) -> put(k, JSONArray().put(v.first).put(v.second)) } })
            put("timer", s.timer?.let {
                JSONObject().apply {
                    put("tipo", it.tipo); put("key", it.key); put("serie", it.serie); put("total", it.total)
                    put("fin", it.fin); put("pausa", it.pausa); put("quedan", it.quedanMs)
                }
            } ?: JSONObject.NULL)
        })
    }

    /** Último ejercicio con series antes de los estiramientos: al acabarlo se para el cronómetro. */
    fun ultimo(dia: Dia): Ejercicio? = dia.ejercicios.lastOrNull { it.numSeries > 0 && !it.clave.startsWith("stretch") }

    /** Marca o desmarca una serie como hecha. Al marcarla arranca el descanso. */
    fun marcar(dia: Dia, ej: Ejercicio, serie: Int, hecha: Boolean = true) {
        val now = System.currentTimeMillis()
        var s = sesion()
        val key = clave(dia.id, ej)
        val set = s.hechas(key).toMutableSet()
        if (hecha) set.add(serie) else set.remove(serie)
        val hechas = s.hechas.toMutableMap().apply { put(key, set) }
        var inicio = s.inicio
        var fin = s.fin
        var timer = s.timer
        var pausa = s.pausa
        var parado = s.parado
        // Otra rutina el mismo día después de terminar una: empieza una sesión nueva.
        if (hecha && fin > 0 && s.dia != dia.id) { inicio = 0L; fin = 0L; timer = null; pausa = 0L; parado = 0L }
        // Hacer una serie con la sesión en pausa la pone en marcha otra vez.
        if (hecha && pausa > 0 && fin == 0L) { parado += now - pausa; pausa = 0L }
        if (hecha) {
            if (inicio == 0L) inicio = now
            // Se guarda el día con los valores (aunque no se hayan cambiado).
            guardarHoy(dia.id, ej, valores(dia.id, ej))
            apuntarSesion(dia.id)
            val esUltima = ej == ultimo(dia) && set.size >= ej.numSeries
            if (esUltima) {
                fin = now
                timer = null
            } else if (ej.descansoSeg > 0) {
                timer = Temporizador("descanso", key, serie, ej.descansoSeg, now + ej.descansoSeg * 1000L, false, 0)
            } else timer = null
        } else if (timer?.key == key && timer.serie == serie) {
            timer = null
        }
        if (!hecha && fin > 0 && ej == ultimo(dia)) { parado += now - fin; fin = 0 }
        // Cronómetro del ejercicio: se para al hacer todas las series y sigue si se desmarca una.
        val tiempos = s.tiempos.toMutableMap()
        val te = tiempos[key]
        val antes = s.hechas(key).size >= ej.numSeries
        if (ej.numSeries > 0 && set.size >= ej.numSeries) {
            if (te != null && te.second == 0L) tiempos[key] = te.first to now // se para con la última serie
        } else if (antes && te != null && te.second > 0) tiempos[key] = (te.first + now - te.second) to 0L // sigue donde se quedó
        s = s.copy(dia = dia.id, inicio = inicio, fin = fin, hechas = hechas, timer = timer, tiempos = tiempos, pausa = pausa, parado = parado)
        guardar(s)
    }

    /** Serie por tiempo (plancha, remo, bici estática): arranca la cuenta atrás de trabajo. */
    fun empezarTrabajo(dia: Dia, ej: Ejercicio, serie: Int) {
        val now = System.currentTimeMillis()
        val s = sesion()
        val key = clave(dia.id, ej)
        val nueva = s.inicio == 0L || (s.fin > 0 && s.dia != dia.id)
        guardar(s.copy(dia = dia.id, inicio = if (nueva) now else s.inicio, fin = if (nueva) 0L else s.fin,
            pausa = if (nueva) 0L else s.pausa, parado = if (nueva) 0L else s.parado,
            timer = Temporizador("trabajo", key, serie, ej.trabajoSeg, now + ej.trabajoSeg * 1000L, false, 0)))
    }

    fun pausar() {
        val s = sesion()
        val t = s.timer ?: return
        val now = System.currentTimeMillis()
        val nt = if (t.pausa) t.copy(pausa = false, fin = now + t.quedanMs) else t.copy(pausa = true, quedanMs = t.restanteMs(now))
        guardar(s.copy(timer = nt))
    }

    fun saltar() {
        val s = sesion()
        val t = s.timer ?: return
        if (t.tipo == "trabajo") {
            // Saltar el trabajo = serie hecha ya
            terminarTrabajo(s, t)
        } else guardar(s.copy(timer = null))
    }

    /** Se llama a menudo desde la interfaz: si acaba una serie por tiempo, la marca hecha y empieza el descanso. */
    fun tick(now: Long = System.currentTimeMillis()) {
        val s = sesion()
        val t = s.timer ?: return
        if (t.tipo == "trabajo" && t.terminado(now)) terminarTrabajo(s, t)
    }

    private fun terminarTrabajo(s: Sesion, t: Temporizador) {
        val diaId = t.key.substringBefore('-')
        val dia = Rutina.dia(diaId) ?: return
        val ej = dia.ejercicios.firstOrNull { clave(dia.id, it) == t.key } ?: return
        marcar(dia, ej, t.serie, true)
    }

    /** Al abrir un ejercicio arranca su cronómetro (una sola vez; no si ya está hecho). */
    fun entrarEjercicio(dia: Dia, ej: Ejercicio) {
        if (ej.numSeries == 0) return
        val s = sesion()
        val key = clave(dia.id, ej)
        if (key in s.tiempos || s.hechas(key).size >= ej.numSeries) return
        guardar(s.copy(tiempos = s.tiempos + (key to (System.currentTimeMillis() to 0L))))
    }

    /**
     * Cronómetro del ejercicio a mano. Se guarda como (inicio, parada): parada = 0 si corre.
     * Al continuar se corre el inicio lo que estuvo parado, así no cuenta ese rato.
     */
    fun cronoEjercicio(dia: Dia, ej: Ejercicio, accion: String) {
        if (accion == "tocar") {
            val te = sesion().tiempos[clave(dia.id, ej)]
            return cronoEjercicio(dia, ej, if (te == null) "iniciar" else if (te.second == 0L) "parar" else "seguir")
        }
        val now = System.currentTimeMillis()
        val s = sesion()
        val key = clave(dia.id, ej)
        val te = s.tiempos[key]
        val nuevo = when (accion) {
            "iniciar" -> now to 0L
            "parar" -> if (te != null && te.second == 0L) te.first to now else return
            "seguir" -> if (te != null && te.second > 0) (te.first + now - te.second) to 0L else return
            else -> return
        }
        guardar(s.copy(tiempos = s.tiempos + (key to nuevo)))
    }

    /**
     * Registro de la sesión para las estadísticas: "gym-AAAA-MM-DD-<rutina>" con tiempo, kcal y pulso.
     * Lo escribe el reloj (con pulso de verdad). Si solo va el móvil, kcal estimadas (est = true),
     * y nunca pisa un registro que ya tenga datos del reloj.
     */
    fun registrar(s: Sesion, kcal: Int?, pm: Int?, pmax: Int?, est: Boolean) {
        val dia = s.dia ?: return
        if (s.inicio == 0L) return
        val k = "gym-${s.fecha}-$dia"
        val prev = Docs.get(k)
        if (est && prev != null && !prev.optBoolean("kcalEst")) return
        Docs.put(k, JSONObject().apply {
            put("fecha", s.fecha); put("dia", dia); put("inicio", Fechas.iso(s.inicio))
            put("dur", s.duracionMs(System.currentTimeMillis()) / 1000)
            put("kcal", kcal ?: JSONObject.NULL); put("pm", pm ?: JSONObject.NULL); put("pmax", pmax ?: JSONObject.NULL)
            put("kcalEst", est)
        })
    }

    /** Deshace «Terminar»: el cronómetro sigue desde donde se quedó (sin contar el rato parado). */
    fun reanudarSesion() {
        val s = sesion()
        if (s.inicio == 0L || s.fin == 0L) return
        val now = System.currentTimeMillis()
        // El inicio no se toca (el reloj guarda las kcal con él); el rato terminado cuenta como parado.
        guardar(s.copy(parado = s.parado + (now - s.fin), fin = 0L, pausa = 0L))
    }

    /** Tocar el tiempo de la sesión: pausa o sigue. Lo parado no cuenta. */
    fun pausarSesion() {
        val s = sesion()
        if (s.inicio == 0L || s.fin > 0) return
        val now = System.currentTimeMillis()
        guardar(if (s.pausa > 0) s.copy(parado = s.parado + (now - s.pausa), pausa = 0L) else s.copy(pausa = now))
    }

    fun terminarSesion() {
        val s = sesion()
        if (s.inicio == 0L) return
        guardar(s.copy(fin = if (s.fin > 0) s.fin else if (s.pausa > 0) s.pausa else System.currentTimeMillis(), pausa = 0L, timer = null))
    }

    fun reiniciarSesion() = Docs.delete(LIVE)

    /**
     * Doble toque en el tiempo de la sesión: se borra la de hoy como si no se hubiera hecho
     * (la rutina vuelve a blanco y se quita de las estadísticas). Los kg y reps apuntados se quedan.
     */
    fun resetearSesion() {
        val s = sesion()
        s.dia?.let { d ->
            quitarSesion(d, s.fecha, borrarSeries = false)
            Docs.delete("gym-${s.fecha}-$d")
        }
        Docs.delete(LIVE)
    }

    private fun apuntarSesion(dia: String) {
        val ses = Docs.get("_sesiones") ?: JSONObject()
        val a = ses.optJSONArray(dia) ?: JSONArray()
        val hoy = Fechas.hoy()
        val list = (0 until a.length()).map { a.optString(it) }.toMutableSet()
        if (list.add(hoy)) {
            ses.put(dia, JSONArray(list.sorted()))
            Docs.put("_sesiones", ses)
        }
    }

    /** Siguiente ejercicio con series por hacer después de [desde]. */
    fun siguiente(dia: Dia, desde: Int): Int? {
        val s = sesion()
        val n = dia.ejercicios.size
        for (k in 1 until n) {
            val i = desde + k
            if (i >= n) break
            val e = dia.ejercicios[i]
            if (e.numSeries == 0 || s.hechas(clave(dia.id, e)).size < e.numSeries) return i
        }
        return null
    }

    /** Ejercicio por el que va (el primero sin terminar). */
    fun actual(dia: Dia): Int {
        val s = sesion()
        return dia.ejercicios.indexOfFirst { it.numSeries > 0 && s.hechas(clave(dia.id, it)).size < it.numSeries }
            .let { if (it < 0) 0 else it }
    }

    fun primeraPendiente(dia: Dia, ej: Ejercicio): Int {
        val h = sesion().hechas(clave(dia.id, ej))
        return (0 until ej.numSeries).firstOrNull { it !in h } ?: (ej.numSeries - 1).coerceAtLeast(0)
    }

    /** Botón «Descanso» de la tabla: arranca (o reinicia) el descanso de esa serie a mano. */
    fun empezarDescanso(dia: Dia, ej: Ejercicio, serie: Int) {
        if (ej.descansoSeg <= 0) return
        val now = System.currentTimeMillis()
        val s = sesion()
        guardar(s.copy(dia = dia.id, timer = Temporizador("descanso", clave(dia.id, ej), serie, ej.descansoSeg,
            now + ej.descansoSeg * 1000L, false, 0)))
    }

    /** Quita el descanso o la serie por tiempo en marcha (la ✕ del recuadro). */
    fun quitarTemporizador() {
        val s = sesion()
        if (s.timer != null) guardar(s.copy(timer = null))
    }

    /** Días entrenados de cada entreno: {"martes": ["2026-10-27", ...]}. */
    fun sesiones(): Map<String, List<String>> {
        val o = Docs.get("_sesiones") ?: return emptyMap()
        val m = HashMap<String, List<String>>()
        o.keys().forEach { k -> val a = o.optJSONArray(k); m[k] = (0 until (a?.length() ?: 0)).map { a!!.optString(it) } }
        return m
    }

    /** Borra un día entrenado (si fue de prueba). */
    fun quitarSesion(dia: String, fecha: String, borrarSeries: Boolean = true) {
        val ses = Docs.get("_sesiones") ?: return
        val a = ses.optJSONArray(dia) ?: return
        val list = (0 until a.length()).map { a.optString(it) }.filter { it != fecha }
        ses.put(dia, JSONArray(list))
        Docs.put("_sesiones", ses)
        // También fuera de la progresión: se quitan las series apuntadas ese día.
        if (borrarSeries) Rutina.dia(dia)?.ejercicios?.forEach { ej ->
            val k = clave(dia, ej)
            val doc = Docs.get(k) ?: return@forEach
            val h = doc.optJSONArray("hist") ?: return@forEach
            val resto = (0 until h.length()).mapNotNull { h.optJSONObject(it) }.filter { it.optString("d") != fecha }
            if (resto.size != h.length()) { doc.put("hist", JSONArray(resto)); Docs.put(k, doc) }
        }
    }

    /**
     * Rutinas marcadas como hechas: las dos últimas que se han hecho, en cualquier día.
     * La que queda en blanco es la que toca. Al terminar esa, se pone en blanco la más antigua de las otras.
     * La que está en marcha ahora mismo no cuenta hasta que se termina.
     */
    fun hechosCiclo(): Set<String> {
        val hoy = Fechas.hoy()
        val s = sesion()
        val todas = sesiones()
        // Último día hecho de cada rutina (sin contar la sesión que está en marcha).
        val ultimo = Rutina.dias.mapNotNull { d ->
            val enMarcha = s.dia == d.id && s.inicio > 0 && s.fin == 0L
            val fechas = (todas[d.id] ?: emptyList()).filter { !(enMarcha && it == hoy) }
            fechas.maxOrNull()?.let { d.id to it }
        }
        // Mismo día: cuenta como más reciente la de la sesión de hoy.
        return ultimo.sortedWith(compareByDescending<Pair<String, String>> { it.second }.thenByDescending { it.first == s.dia })
            .take(Rutina.dias.size - 1).map { it.first }.toSet()
    }
}
