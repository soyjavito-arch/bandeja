package com.bandeja.padel

enum class Team {
    A, B;

    val other: Team get() = if (this == A) B else A
}

/** Cómo se resuelve el 40-40. */
enum class DeuceMode {
    /** Punto de oro: a 40-40 el siguiente punto gana el juego. */
    GOLDEN,
    /** Iguales y ventaja de toda la vida. */
    ADVANTAGE,
    /** Star point: dos ventajas normales; en el tercer 40-40 hay punto decisivo. */
    STAR_POINT,
}

/** Reglas del partido. */
data class Rules(
    val deuceMode: DeuceMode = DeuceMode.GOLDEN,
    /** Sets necesarios para ganar (2 = al mejor de 3). */
    val setsToWin: Int = 2,
    /** El set decisivo se juega como super tie-break a 10. */
    val superTieBreak: Boolean = false,
)

/** Un set terminado. Si fue super tie-break, a y b son los puntos. */
data class SetScore(val a: Int, val b: Int, val superTieBreak: Boolean = false) {
    val winner: Team get() = if (a > b) Team.A else Team.B
}

/**
 * Estado del partido. Se calcula siempre a partir de la lista de puntos,
 * así deshacer es simplemente quitar el último punto.
 */
data class MatchState(
    val rules: Rules,
    val pointsA: Int = 0,
    val pointsB: Int = 0,
    val gamesA: Int = 0,
    val gamesB: Int = 0,
    val sets: List<SetScore> = emptyList(),
    val server: Team = Team.A,
    val tieBreak: Boolean = false,
    val superTieBreak: Boolean = false,
    /** Quién sacó el primer punto del tie-break actual. */
    val tieBreakFirstServer: Team = Team.A,
    /** Veces que se ha llegado a 40-40 en el juego actual. */
    val deuces: Int = 0,
    /** Juegos jugados en todo el partido (un tie-break cuenta como un juego). */
    val totalGames: Int = 0,
    /** Cambios de lado que tocan hasta ahora (crece cada vez que hay que cambiar). */
    val endChanges: Int = 0,
    /** Turnos de saque que ha empezado cada pareja en el set actual (incluido el actual). */
    val serveTurnsA: Int = 0,
    val serveTurnsB: Int = 0,
    val winner: Team? = null,
) {
    fun points(t: Team) = if (t == Team.A) pointsA else pointsB
    fun serveTurns(t: Team) = if (t == Team.A) serveTurnsA else serveTurnsB

    /** Número del set que se está jugando (0 = primero). */
    val setIndex: Int get() = sets.size

    /**
     * Cada pareja elige quién saca primero solo en su primer juego de saque de cada set
     * y antes de jugar el primer punto. Después el orden es fijo hasta acabar el set.
     */
    val canChooseServerPlayer: Boolean
        get() = !isFinished && pointsA == 0 && pointsB == 0 && serveTurns(server) == 1

    /**
     * Qué jugador de la pareja que saca está sacando: los dos jugadores se turnan.
     * firstIsDrive = en su primer turno del set sacó el de la derecha.
     */
    fun serverIsDrive(firstIsDrive: Boolean): Boolean =
        (serveTurns(server) % 2 == 1) == firstIsDrive
    fun games(t: Team) = if (t == Team.A) gamesA else gamesB
    fun setsWon(t: Team) = sets.count { it.winner == t }

    val isFinished: Boolean get() = winner != null

    /** 40-40 en el que el siguiente punto decide el juego. */
    val isDecidingPoint: Boolean
        get() = !tieBreak && pointsA == 3 && pointsB == 3 && when (rules.deuceMode) {
            DeuceMode.GOLDEN -> true
            DeuceMode.ADVANTAGE -> false
            DeuceMode.STAR_POINT -> deuces >= 3
        }

    /** Texto para mostrar cuando hay punto decisivo. */
    val decidingLabel: String?
        get() = if (!isDecidingPoint) null
        else if (rules.deuceMode == DeuceMode.STAR_POINT) "STAR POINT" else "PUNTO DE ORO"

    /** Texto del marcador de puntos para un equipo: 0, 15, 30, 40, AD. */
    fun pointLabel(t: Team): String {
        val p = points(t)
        if (tieBreak) return p.toString()
        return when (p) {
            0 -> "0"
            1 -> "15"
            2 -> "30"
            3 -> "40"
            else -> "AD"
        }
    }

    fun addPoint(t: Team): MatchState {
        val r = addPointRaw(t)
        if (r === this || r.isFinished) return r
        // Nuevo turno de saque cuando cambia quien saca o empieza un juego nuevo.
        val newTurn = r.server != server || r.totalGames != totalGames
        if (!newTurn) return r
        return if (r.server == Team.A) r.copy(serveTurnsA = r.serveTurnsA + 1)
        else r.copy(serveTurnsB = r.serveTurnsB + 1)
    }

    private fun addPointRaw(t: Team): MatchState {
        if (isFinished) return this
        var pa = pointsA
        var pb = pointsB
        if (t == Team.A) pa++ else pb++
        val p = if (t == Team.A) pa else pb
        val o = if (t == Team.A) pb else pa

        if (tieBreak) {
            val target = if (superTieBreak) 10 else 7
            if (p >= target && p - o >= 2) return winTieBreak(t, pa, pb)
            // En el tie-break el saque cambia tras el 1er punto y luego cada 2.
            val played = pa + pb
            val newServer = if (played % 2 == 1) server.other else server
            // En el tie-break se cambia de lado cada 6 puntos.
            val change = if (played % 6 == 0) 1 else 0
            return copy(pointsA = pa, pointsB = pb, server = newServer, endChanges = endChanges + change)
        }

        if (isDecidingPoint) return winGame(t)
        if (p >= 4 && p - o >= 2) return winGame(t)
        var d = deuces
        if (pa == 4 && pb == 4) { pa = 3; pb = 3 } // vuelta a iguales
        if (pa == 3 && pb == 3) d++
        return copy(pointsA = pa, pointsB = pb, deuces = d)
    }

    /** Suma un juego al total del partido; se cambia de lado cuando el total es impar. */
    private fun countGame(): MatchState {
        val total = totalGames + 1
        return copy(totalGames = total, endChanges = endChanges + if (total % 2 == 1) 1 else 0)
    }

    private fun winGame(t: Team): MatchState = countGame().winGameCounted(t)

    private fun winGameCounted(t: Team): MatchState {
        val ga = gamesA + if (t == Team.A) 1 else 0
        val gb = gamesB + if (t == Team.B) 1 else 0
        val g = if (t == Team.A) ga else gb
        val o = if (t == Team.A) gb else ga
        val nextServer = server.other
        if (g >= 6 && g - o >= 2) {
            return finishSet(SetScore(ga, gb), nextServer)
        }
        if (ga == 6 && gb == 6) {
            return copy(
                pointsA = 0, pointsB = 0, gamesA = ga, gamesB = gb, deuces = 0,
                server = nextServer, tieBreak = true, superTieBreak = false,
                tieBreakFirstServer = nextServer,
            )
        }
        return copy(pointsA = 0, pointsB = 0, gamesA = ga, gamesB = gb, deuces = 0, server = nextServer)
    }

    private fun winTieBreak(t: Team, pa: Int, pb: Int): MatchState =
        countGame().winTieBreakCounted(t, pa, pb)

    private fun winTieBreakCounted(t: Team, pa: Int, pb: Int): MatchState {
        // Tras un tie-break saca primero quien restó al inicio del tie-break.
        val nextServer = tieBreakFirstServer.other
        val set = if (superTieBreak) {
            SetScore(pa, pb, superTieBreak = true)
        } else {
            SetScore(gamesA + if (t == Team.A) 1 else 0, gamesB + if (t == Team.B) 1 else 0)
        }
        return finishSet(set, nextServer)
    }

    private fun finishSet(set: SetScore, nextServer: Team): MatchState {
        val newSets = sets + set
        val wa = newSets.count { it.winner == Team.A }
        val wb = newSets.count { it.winner == Team.B }
        val base = copy(
            pointsA = 0, pointsB = 0, gamesA = 0, gamesB = 0, deuces = 0, sets = newSets,
            serveTurnsA = 0, serveTurnsB = 0, // en cada set nuevo se vuelve a elegir el orden
            server = nextServer, tieBreak = false, superTieBreak = false,
        )
        if (wa >= rules.setsToWin) return base.copy(winner = Team.A)
        if (wb >= rules.setsToWin) return base.copy(winner = Team.B)
        val decider = wa == rules.setsToWin - 1 && wb == rules.setsToWin - 1
        if (rules.superTieBreak && decider) {
            return base.copy(tieBreak = true, superTieBreak = true, tieBreakFirstServer = nextServer)
        }
        return base
    }

    companion object {
        fun replay(rules: Rules, firstServer: Team, history: List<Team>): MatchState =
            history.fold(
                MatchState(
                    rules = rules,
                    server = firstServer,
                    serveTurnsA = if (firstServer == Team.A) 1 else 0,
                    serveTurnsB = if (firstServer == Team.B) 1 else 0,
                )
            ) { s, t -> s.addPoint(t) }
    }
}
