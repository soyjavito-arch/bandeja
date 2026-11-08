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
) {
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
            .apply()
    }
}
