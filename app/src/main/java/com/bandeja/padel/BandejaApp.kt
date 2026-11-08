package com.bandeja.padel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
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
import androidx.compose.ui.text.font.FontStyle
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

/**
 * Pantalla del marcador: la esfera se parte en dos mitades (azul arriba, rojo abajo).
 * Tocar una mitad suma el punto a esa pareja. Todo se coloca en fracciones del
 * diámetro de la esfera (s) para aprovechar toda la pantalla redonda.
 */
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
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val s = if (maxWidth < maxHeight) maxWidth else maxHeight
        val left = (maxWidth - s) / 2
        val top = (maxHeight - s) / 2
        fun f(x: Float): Dp = s * x
        // Tamaño de letra fijo respecto a la esfera (no depende del tamaño de fuente del sistema).
        fun fs(x: Float): TextUnit = with(density) { (s * x).toSp() }

        // Las dos mitades (son los botones de punto)
        Team.entries.forEach { t ->
            val y0 = if (t == Team.A) 0f else 0.5f
            val shape = with(density) {
                DialCutShape(
                    offX = 0f,
                    offY = f(y0).toPx(),
                    center = (s / 2).toPx(),
                    radius = (s / 2).toPx(),
                    corner = 0f,
                )
            }
            val brush = if (t == Team.A) Brush.verticalGradient(listOf(BlueBottom, BlueTop))
            else Brush.verticalGradient(listOf(RedTop, RedBottom))
            Box(
                Modifier.offset(left, top + f(y0)).size(s, f(0.5f))
                    .clip(shape).background(brush).clickable { onPoint(t) }
            )
        }

        // Hora y, debajo, el pulso
        Placed(left, top + f(0.025f), s, f(0.125f)) {
            Text(clock.format(Date(now)), fontSize = fs(0.11f), fontWeight = FontWeight.Bold, color = Color.White)
        }
        Placed(left, top + f(0.14f), s, f(0.095f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♥", fontSize = fs(0.07f), color = RedText)
                Spacer(Modifier.width(f(0.015f)))
                Text(if (heartRate > 0) "$heartRate" else "--", fontSize = fs(0.088f), fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
        val info: String? = when {
            state.decidingLabel != null -> state.decidingLabel
            state.superTieBreak -> "SUPER TB"
            state.tieBreak -> "TIE-BREAK"
            saved.history.isEmpty() -> "SACA ${teamName(state.server).uppercase()}"
            else -> null
        }

        // Juegos por set y puntos de cada pareja
        Team.entries.forEach { t ->
            val gy = if (t == Team.A) 0.255f else 0.615f // fila de juegos
            val py = if (t == Team.A) 0.19f else 0.565f // puntos
            if (state.server == t) {
                Placed(left + f(0.085f), top + f(gy + 0.03f), f(0.08f), f(0.08f)) {
                    ServeBall(Modifier.size(f(0.075f)))
                }
            }
            Box(
                Modifier.offset(left + f(0.18f), top + f(gy)).size(f(0.34f), f(0.14f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(f(0.025f)), verticalAlignment = Alignment.CenterVertically) {
                    state.sets.forEach { set ->
                        val mine = if (t == Team.A) set.a else set.b
                        val won = set.winner == t
                        Text(
                            "$mine",
                            fontSize = fs(if (set.superTieBreak) 0.08f else 0.115f),
                            fontWeight = FontWeight.ExtraBold,
                            color = if (won) Color.White else Color(0x99FFFFFF),
                        )
                    }
                    if (!state.superTieBreak || state.sets.isEmpty()) {
                        Text("${state.games(t)}", fontSize = fs(0.13f), fontWeight = FontWeight.ExtraBold, color = Gold)
                    }
                }
            }
            Placed(left + f(0.49f), top + f(py), f(0.44f), f(0.25f)) {
                val label = state.pointLabel(t)
                Text(
                    label,
                    fontSize = fs(if (label.length > 2) 0.23f else 0.30f),
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }

        // Franja central: deshacer, cronómetro + pulso, menú
        Box(Modifier.offset(left, top + f(0.5f) - 1.dp).size(s, 2.dp).background(Color(0x55FFFFFF)))
        Placed(left + f(0.045f), top + f(0.44f), f(0.12f), f(0.12f)) {
            RoundIconButton("↶", f(0.12f), fs(0.075f), enabled = saved.history.isNotEmpty(), onClick = onUndo)
        }
        Placed(left + f(0.835f), top + f(0.44f), f(0.12f), f(0.12f)) {
            RoundIconButton("⋯", f(0.12f), fs(0.075f), enabled = true, onClick = onMenu)
        }
        Placed(left + f(0.17f), top + f(0.45f), f(0.66f), f(0.1f)) {
            Row(
                Modifier.clip(RoundedCornerShape(f(0.045f))).background(Color(0xF00B1020))
                    .padding(horizontal = f(0.03f), vertical = f(0.008f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val timerColor = if (saved.timerRunning) Gold else Dim
                Text("⏱", fontSize = fs(0.055f), color = timerColor)
                Spacer(Modifier.width(f(0.012f)))
                Text(formatDuration(saved.elapsedMs(now)), fontSize = fs(0.07f), fontWeight = FontWeight.ExtraBold, color = timerColor)
                if (info != null) {
                    Spacer(Modifier.width(f(0.03f)))
                    Text(info, fontSize = fs(0.042f), fontWeight = FontWeight.ExtraBold, color = Gold, maxLines = 1)
                }
            }
        }
    }
}

/** Caja colocada en una posición absoluta con el contenido centrado. */
@Composable
private fun Placed(x: Dp, y: Dp, w: Dp, h: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.offset(x, y).size(w, h), contentAlignment = Alignment.Center, content = content)
}

/** Rectángulo (opcionalmente redondeado) recortado por el círculo de la esfera. */
private class DialCutShape(
    private val offX: Float,
    private val offY: Float,
    private val center: Float,
    private val radius: Float,
    private val corner: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val rect = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner, corner)))
        }
        val cx = center - offX
        val cy = center - offY
        val circle = Path().apply { addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius)) }
        return Outline.Generic(Path.combine(PathOperation.Intersect, rect, circle))
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
private fun RoundIconButton(
    symbol: String,
    size: Dp = 38.dp,
    fontSize: TextUnit = 20.sp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(ButtonDark)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = fontSize, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else Color(0xFF5A5E66))
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
        Text("¡Gana ${teamName(w)}!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = teamColor(w))
        Spacer(Modifier.height(6.dp))
        Text(
            state.sets.joinToString("   ") { if (it.superTieBreak) "[${it.a}-${it.b}]" else "${it.a}-${it.b}" },
            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White,
        )
        Text("⏱ " + formatDuration(saved.elapsedMs(System.currentTimeMillis())), fontSize = 16.sp, color = Dim)
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
