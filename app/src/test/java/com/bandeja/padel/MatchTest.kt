package com.bandeja.padel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchTest {
    private fun play(rules: Rules, vararg seq: Pair<Team, Int>): MatchState {
        val h = seq.flatMap { (t, n) -> List(n) { t } }
        return MatchState.replay(rules, Team.A, h)
    }

    @Test fun pointLabels() {
        val s = play(Rules(), Team.A to 2, Team.B to 3)
        assertEquals("30", s.pointLabel(Team.A))
        assertEquals("40", s.pointLabel(Team.B))
    }

    @Test fun advantageWithoutGoldenPoint() {
        val r = Rules(deuceMode = DeuceMode.ADVANTAGE)
        var s = play(r, Team.A to 3, Team.B to 3, Team.A to 1)
        assertEquals("AD", s.pointLabel(Team.A))
        s = s.addPoint(Team.B)
        assertEquals("40", s.pointLabel(Team.A)); assertEquals("40", s.pointLabel(Team.B))
        s = s.addPoint(Team.B).addPoint(Team.B)
        assertEquals(1, s.gamesB)
    }

    @Test fun goldenPointWinsGame() {
        val s = play(Rules(deuceMode = DeuceMode.GOLDEN), Team.A to 3, Team.B to 3)
        assertTrue(s.isDecidingPoint)
        val s2 = s.addPoint(Team.B)
        assertEquals(1, s2.gamesB); assertEquals(0, s2.pointsA)
        assertEquals(Team.B, s2.server)
    }

    @Test fun setAt6_4AndTieBreak() {
        val s = play(Rules(), Team.A to 4 * 5, Team.B to 4 * 4, Team.A to 4)
        assertEquals(listOf(SetScore(6, 4)), s.sets)
        // 6-6 -> tie-break
        val t = play(Rules(), Team.A to 4 * 5, Team.B to 4 * 6, Team.A to 4)
        assertTrue(t.tieBreak)
        val tbServer = t.server
        val t1 = t.addPoint(Team.A)
        assertEquals(tbServer.other, t1.server)
        val done = (1..6).fold(t1) { x, _ -> x.addPoint(Team.A) }
        assertEquals(SetScore(7, 6), done.sets.last())
        assertEquals(tbServer.other, done.server)
    }

    @Test fun matchWinAndSuperTieBreak() {
        val r = Rules(superTieBreak = true)
        val s = play(r, Team.A to 24, Team.B to 24)
        assertTrue(s.tieBreak && s.superTieBreak)
        val end = play(r, Team.A to 24, Team.B to 24, Team.A to 10)
        assertEquals(Team.A, end.winner)
        assertEquals(SetScore(10, 0, true), end.sets.last())
        val full = play(Rules(), Team.A to 48)
        assertEquals(Team.A, full.winner)
    }

    @Test fun starPoint() {
        val r = Rules(deuceMode = DeuceMode.STAR_POINT)
        // 1er 40-40: ventaja normal
        var s = play(r, Team.A to 3, Team.B to 3)
        assertEquals(false, s.isDecidingPoint)
        s = s.addPoint(Team.A); assertEquals("AD", s.pointLabel(Team.A))
        s = s.addPoint(Team.B) // 2º 40-40
        assertEquals(false, s.isDecidingPoint)
        s = s.addPoint(Team.B).addPoint(Team.A) // 3er 40-40
        assertTrue(s.isDecidingPoint)
        assertEquals("STAR POINT", s.decidingLabel)
        s = s.addPoint(Team.B)
        assertEquals(1, s.gamesB); assertEquals(0, s.deuces)
    }
}
