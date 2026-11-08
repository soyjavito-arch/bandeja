package com.bandeja.padel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Colores propios de Bandeja
private val BlueTop = Color(0xFF4A86FF)
private val BlueBottom = Color(0xFF1C47C9)
private val RedTop = Color(0xFFFF5F66)
private val RedBottom = Color(0xFFC4223A)
private val BlueText = Color(0xFF5B95FF)
private val RedText = Color(0xFFFF6B72)
private val Gold = Color(0xFFFFC93C)
private val Dim = Color(0xFF8A8F98)
private val ButtonDark = Color(0xFF26292E)

private fun teamName(t: Team) = if (t == Team.A) "Azul" else "Rojo"
private fun teamColor(t: Team) = if (t == Team.A) BlueText else RedText

@Composable
fun BandejaApp(
    saved: Saved,
    heartRate: Int,
    onPoint: (Team) -> Unit,
    onUndo: () -> Unit,
    onChange: (Saved) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (showMenu) {
                BackHandler { showMenu = false }
                MenuScreen(saved, onChange, onClose = { showMenu = false })
            } else {
                val state = saved.state()
                if (state.isFinished) {
                    FinishedScreen(saved, state, onUndo, onNew = {
                        onChange(saved.copy(history = emptyList(), timerStartedAt = 0L, timerAccumMs = 0L))
                    })
                } else {
                    ScoreScreen(saved, state, heartRate, onPoint, onUndo, onMenu = { showMenu = true })
                }
            }
        }
    }
}

/** Reloj que se actualiza cada segundo. */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    return now
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun ScoreScreen(
    saved: Saved,
    state: MatchState,
    heartRate: Int,
    onPoint: (Team) -> Unit,
    onUndo: () -> Unit,
    onMenu: () -> Unit,
) {
    val now = rememberNow()
    val screen = LocalConfiguration.current.screenWidthDp
    val btn = (screen * 0.34f).dp
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        Modifier.fillMaxSize().padding(top = (screen * 0.045f).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Hora
        Text(clock.format(Date(now)), fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        // Pulso y cronómetro
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("♥", fontSize = 10.sp, lineHeight = 13.sp, color = RedText)
            Spacer(Modifier.width(2.dp))
            Text(if (heartRate > 0) "$heartRate" else "--", fontSize = 11.sp, lineHeight = 13.sp, color = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(
                "⏱ " + formatDuration(saved.elapsedMs(now)),
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = if (saved.timerRunning) Color.White else Dim,
            )
        }
        // Juegos del set actual
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${state.gamesA}", fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = BlueText)
            Text("  -  ", fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("${state.gamesB}", fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = RedText)
        }
        // Línea de info: punto decisivo, tie-break o sets anteriores
        val info: Pair<String, Color> = when {
            state.decidingLabel != null -> state.decidingLabel!! to Gold
            state.superTieBreak -> "SUPER TIE-BREAK" to Gold
            state.tieBreak -> "TIE-BREAK" to Gold
            state.sets.isNotEmpty() -> state.sets.joinToString("   ") {
                if (it.superTieBreak) "[${it.a}-${it.b}]" else "${it.a}-${it.b}"
            } to Dim
            saved.history.isEmpty() -> "Saca ${teamName(state.server)}" to Dim
            else -> "" to Dim
        }
        Text(info.first, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = info.second, maxLines = 1)
        Spacer(Modifier.height(2.dp))

        // Botones de punto
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Team.entries.forEach { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SetDots(won = state.setsWon(t), total = state.rules.setsToWin)
                    Spacer(Modifier.height(4.dp))
                    PointButton(
                        label = state.pointLabel(t),
                        team = t,
                        serving = state.server == t,
                        size = btn,
                        onClick = { onPoint(t) },
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        // Deshacer y menú
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIconButton("↶", enabled = saved.history.isNotEmpty(), onClick = onUndo)
            RoundIconButton("⋯", enabled = true, onClick = onMenu)
        }
    }
}

@Composable
private fun SetDots(won: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(if (i < won) Gold else Color(0xFF4A4E55))
            )
        }
    }
}

@Composable
private fun PointButton(label: String, team: Team, serving: Boolean, size: Dp, onClick: () -> Unit) {
    val brush = if (team == Team.A) Brush.verticalGradient(listOf(BlueTop, BlueBottom))
    else Brush.verticalGradient(listOf(RedTop, RedBottom))
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.26f)).background(brush).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = (size.value * if (label.length > 2) 0.36f else 0.46f).sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        if (serving) {
            ServeBall(Modifier.align(Alignment.TopStart).padding(7.dp).size(size * 0.18f))
        }
    }
}

/** Pelota de pádel dibujada a mano: indica quién saca. */
@Composable
private fun ServeBall(modifier: Modifier) {
    Canvas(modifier) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.11f)
        drawCircle(Gold, radius = w / 2f)
        drawArc(
            Color.White, startAngle = -60f, sweepAngle = 120f, useCenter = false,
            topLeft = Offset(-w * 0.55f, w * 0.05f), size = Size(w * 0.9f, w * 0.9f), style = stroke,
        )
        drawArc(
            Color.White, startAngle = 120f, sweepAngle = 120f, useCenter = false,
            topLeft = Offset(w * 0.65f, w * 0.05f), size = Size(w * 0.9f, w * 0.9f), style = stroke,
        )
    }
}

@Composable
private fun RoundIconButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(ButtonDark)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 18.sp, color = if (enabled) Color.White else Color(0xFF5A5E66))
    }
}

@Composable
private fun FinishedScreen(saved: Saved, state: MatchState, onUndo: () -> Unit, onNew: () -> Unit) {
    val w = state.winner ?: return
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("¡Gana ${teamName(w)}!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = teamColor(w))
        Spacer(Modifier.height(6.dp))
        Text(
            state.sets.joinToString("   ") { if (it.superTieBreak) "[${it.a}-${it.b}]" else "${it.a}-${it.b}" },
            fontSize = 18.sp, color = Color.White,
        )
        Text("⏱ " + formatDuration(saved.elapsedMs(System.currentTimeMillis())), fontSize = 13.sp, color = Dim)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton("↶", enabled = true, onClick = onUndo)
            Box(
                Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(Gold)
                    .clickable(onClick = onNew).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Nuevo partido", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
        }
    }
}

@Composable
private fun MenuScreen(saved: Saved, onChange: (Saved) -> Unit, onClose: () -> Unit) {
    val listState = rememberScalingLazyListState()
    var confirmNew by remember { mutableStateOf(false) }
    val r = saved.rules
    fun setRules(nr: Rules) = onChange(saved.copy(rules = nr))

    ScalingLazyColumn(Modifier.fillMaxSize(), state = listState) {
        item { Text("Ajustes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        item {
            MenuChip(
                if (confirmNew) "¿Seguro? Toca otra vez" else "Nuevo partido",
                "Pone el marcador a cero",
                highlight = true,
            ) {
                if (confirmNew) {
                    onChange(saved.copy(history = emptyList(), timerStartedAt = 0L, timerAccumMs = 0L))
                    onClose()
                } else confirmNew = true
            }
        }
        item {
            val label = when (r.deuceMode) {
                DeuceMode.GOLDEN -> "Punto de oro"
                DeuceMode.ADVANTAGE -> "Ventajas (iguales)"
                DeuceMode.STAR_POINT -> "Star point"
            }
            MenuChip("40-40: $label", "Toca para cambiar") {
                val next = DeuceMode.entries[(r.deuceMode.ordinal + 1) % DeuceMode.entries.size]
                setRules(r.copy(deuceMode = next))
            }
        }
        item {
            MenuChip(
                if (r.setsToWin == 1) "Partido a 1 set" else "Al mejor de 3 sets",
                "Toca para cambiar",
            ) { setRules(r.copy(setsToWin = if (r.setsToWin == 1) 2 else 1)) }
        }
        if (r.setsToWin > 1) {
            item {
                MenuChip(
                    "Set decisivo: " + if (r.superTieBreak) "super tie-break" else "normal",
                    "Super tie-break a 10",
                ) { setRules(r.copy(superTieBreak = !r.superTieBreak)) }
            }
        }
        item {
            MenuChip("Saca primero: ${teamName(saved.firstServer)}", "Toca para cambiar") {
                onChange(saved.copy(firstServer = saved.firstServer.other))
            }
        }
        item {
            val now = System.currentTimeMillis()
            MenuChip(
                if (saved.timerRunning) "Pausar cronómetro" else "Reanudar cronómetro",
                "Arranca solo con el primer punto",
            ) {
                onChange(if (saved.timerRunning) saved.pauseTimer(now) else saved.startTimer(now))
            }
        }
        item {
            MenuChip(
                "Pantalla siempre encendida: " + if (saved.keepScreenOn) "sí" else "no",
                "Gasta algo más de batería",
            ) { onChange(saved.copy(keepScreenOn = !saved.keepScreenOn)) }
        }
        item { MenuChip("Volver al marcador", null, onClick = onClose) }
    }
}

@Composable
private fun MenuChip(label: String, secondary: String?, highlight: Boolean = false, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 2) },
        secondaryLabel = if (secondary != null) {
            { Text(secondary, maxLines = 1, color = Dim) }
        } else null,
        colors = if (highlight) ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF1C47C9))
        else ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}
