package com.bandeja.padel

import android.content.Context

/** Todo lo que se guarda para no perder el partido si la app se cierra. */
data class Saved(
    val rules: Rules = Rules(),
    val firstServer: Team = Team.A,
    val history: List<Team> = emptyList(),
    /** Momento (ms) en que arrancó el cronómetro, 0 si está parado. */
    val timerStartedAt: Long = 0L,
    /** Tiempo acumulado antes del último arranque. */
    val timerAccumMs: Long = 0L,
    val keepScreenOn: Boolean = true,
    /** Ya se ha elegido quién saca (con el primer toque del partido). */
    val serverChosen: Boolean = false,
    /** Cambios de lado ya vistos (el aviso sale hasta que se toca la pantalla). */
    val endChangesSeen: Int = 0,
    /**
     * Quién sacó primero en cada set, por pareja: una letra por set,
     * 'D' (jugador de la derecha) o 'R' (jugador del revés).
     */
    val orderA: String = "",
    val orderB: String = "",
) {
    fun firstDrive(t: Team, set: Int): Boolean =
        (if (t == Team.A) orderA else orderB).getOrElse(set) { 'D' } == 'D'

    /** Fija si el primer saque de la pareja en ese set lo hace el de la derecha (D) o el del revés (R). */
    fun setOrder(t: Team, set: Int, drive: Boolean): Saved {
        val cur = (if (t == Team.A) orderA else orderB).padEnd(set + 1, 'D')
        val updated = cur.substring(0, set) + (if (drive) 'D' else 'R') + cur.substring(set + 1)
        return if (t == Team.A) copy(orderA = updated) else copy(orderB = updated)
    }

    /** Cambia D/R del primer saque de la pareja en ese set. */
    fun toggleServerPlayer(t: Team, set: Int): Saved {
        val cur = (if (t == Team.A) orderA else orderB).padEnd(set + 1, 'D')
        val flipped = cur.substring(0, set) + (if (cur[set] == 'D') 'R' else 'D') + cur.substring(set + 1)
        return if (t == Team.A) copy(orderA = flipped) else copy(orderB = flipped)
    }

    val serverPicked: Boolean get() = serverChosen || history.isNotEmpty()

    val timerRunning: Boolean get() = timerStartedAt != 0L

    fun elapsedMs(now: Long): Long =
        timerAccumMs + if (timerRunning) (now - timerStartedAt).coerceAtLeast(0) else 0

    fun startTimer(now: Long) = if (timerRunning) this else copy(timerStartedAt = now)
    fun pauseTimer(now: Long) =
        if (!timerRunning) this else copy(timerAccumMs = elapsedMs(now), timerStartedAt = 0L)

    fun state(): MatchState = MatchState.replay(rules, firstServer, history)
}

class MatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("bandeja", Context.MODE_PRIVATE)

    fun load(): Saved {
        val p = prefs
        val mode = runCatching { DeuceMode.valueOf(p.getString("deuceMode", null) ?: "") }
            .getOrDefault(DeuceMode.GOLDEN)
        return Saved(
            rules = Rules(
                deuceMode = mode,
                setsToWin = p.getInt("setsToWin", 2).coerceIn(1, 3),
                superTieBreak = p.getBoolean("superTieBreak", false),
            ),
            firstServer = if (p.getString("firstServer", "A") == "B") Team.B else Team.A,
            history = (p.getString("history", "") ?: "").mapNotNull {
                when (it) { 'A' -> Team.A; 'B' -> Team.B; else -> null }
            },
            timerStartedAt = p.getLong("timerStartedAt", 0L),
            timerAccumMs = p.getLong("timerAccumMs", 0L),
            keepScreenOn = p.getBoolean("keepScreenOn", true),
            serverChosen = p.getBoolean("serverChosen", false),
            endChangesSeen = p.getInt("endChangesSeen", 0),
            orderA = p.getString("orderA", "") ?: "",
            orderB = p.getString("orderB", "") ?: "",
        )
    }

    fun save(s: Saved) {
        prefs.edit()
            .putString("deuceMode", s.rules.deuceMode.name)
            .putInt("setsToWin", s.rules.setsToWin)
            .putBoolean("superTieBreak", s.rules.superTieBreak)
            .putString("firstServer", s.firstServer.name)
            .putString("history", s.history.joinToString("") { it.name })
            .putLong("timerStartedAt", s.timerStartedAt)
            .putLong("timerAccumMs", s.timerAccumMs)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("serverChosen", s.serverChosen)
            .putInt("endChangesSeen", s.endChangesSeen)
            .putString("orderA", s.orderA)
            .putString("orderB", s.orderB)
            .apply()
    }
}
