package com.bandeja.padel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlin.random.Random

// Colores propios de Bandeja
private val BlueTop = Color(0xFF16357F)
private val BlueBottom = Color(0xFF081633)
private val GreenTop = Color(0xFF0F5A40)
private val GreenBottom = Color(0xFF04140E)
private val BlueText = Color(0xFF5B95FF)
private val RedText = Color(0xFFFF6B72)
private val GreenText = Color(0xFF4FE3A3)
private val Gold = Color(0xFFFFC93C)
private val Dim = Color(0xFF8A8F98)
private val ButtonDark = Color(0xFF26292E)

private fun teamName(t: Team) = if (t == Team.A) "Azul" else "Verde"
private fun teamColor(t: Team) = if (t == Team.A) BlueText else GreenText

@Composable
fun BandejaApp(
    saved: Saved,
    heartRate: Int,
    onPoint: (Team) -> Unit,
    onUndo: () -> Unit,
    onChange: (Saved) -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showToss by remember { mutableStateOf(false) }
    // Al empezar un partido el botón de sorteo sale en grande hasta que se usa.
    var tossDone by remember { mutableStateOf(false) }
    val change: (Saved) -> Unit = { n ->
        if (n.history.isEmpty() && !n.serverChosen && saved.history.isNotEmpty()) tossDone = false
        onChange(n)
    }
    val closeToss = {
        showToss = false
        tossDone = true
    }
    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (showToss) {
                BackHandler { closeToss() }
                CoinTossScreen(onClose = closeToss)
            } else if (showMenu) {
                BackHandler { showMenu = false }
                MenuScreen(saved, change, onClose = { showMenu = false }, onToss = {
                    showMenu = false
                    showToss = true
                })
            } else {
                val state = saved.state()
                if (state.isFinished) {
                    FinishedScreen(saved, state, onUndo, onNew = {
                        change(saved.copy(history = emptyList(), timerStartedAt = 0L, timerAccumMs = 0L, serverChosen = false, endChangesSeen = 0))
                    })
                } else {
                    ScoreScreen(
                        saved, state, heartRate, onPoint, onUndo,
                        onMenu = { showMenu = true },
                        onToss = { showToss = true },
                        bigToss = !tossDone,
                        onToggleServerPlayer = { change(saved.toggleServerPlayer(state.server)) },
                    )
                    // Aviso de cambio de lado: se queda hasta que se toca la pantalla
                    if (state.endChanges > saved.endChangesSeen) {
                        ChangeEndsOverlay(
                            tieBreak = state.tieBreak,
                            onDismiss = { change(saved.copy(endChangesSeen = state.endChanges)) },
                        )
                    }
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
    return "%02d:%02d:%02d".format(h, m, s)
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
    onToss: () -> Unit,
    bigToss: Boolean,
    onToggleServerPlayer: () -> Unit,
) {
    val now = rememberNow()
    val showBigToss = bigToss && !saved.serverPicked
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
            else Brush.verticalGradient(listOf(GreenTop, GreenBottom))
            Box(
                Modifier.offset(left, top + f(y0)).size(s, f(0.5f))
                    .clip(shape).background(brush).clickable { onPoint(t) }
            )
        }

        // Punto decisivo (punto de oro / star point): aro dorado que late y tinte dorado
        val deciding = state.isDecidingPoint
        if (deciding) {
            val pulse = rememberInfiniteTransition(label = "oro")
            val glow by pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
                label = "brillo",
            )
            Canvas(Modifier.offset(left, top).size(s)) {
                val ring = f(0.04f).toPx()
                val r = size.minDimension / 2f
                drawCircle(Gold.copy(alpha = 0.22f * glow), radius = r)
                drawCircle(Gold.copy(alpha = glow), radius = r - ring / 2f, style = Stroke(ring))
            }
        }

        // Hora arriba
        Placed(left, top + f(0.03f), s, f(0.13f)) {
            Text(clock.format(Date(now)), fontSize = fs(0.11f), fontWeight = FontWeight.Bold, color = Color.White)
        }
        val info: String? = when {
            state.decidingLabel != null -> if (state.rules.deuceMode == DeuceMode.STAR_POINT) "STAR" else "ORO"
            state.superTieBreak -> "SUPER TB"
            state.tieBreak -> "TIE-BREAK"
            !saved.serverPicked -> "¿QUIÉN SACA?"
            saved.history.isEmpty() -> "SACA ${teamName(state.server).uppercase()}"
            else -> null
        }
        // Aviso (punto de oro, tie-break...) debajo de la hora
        if (info != null && !showBigToss) {
            Placed(left, top + f(0.15f), s, f(0.06f)) {
                Text(info, fontSize = fs(0.055f), fontWeight = FontWeight.ExtraBold, color = Gold, maxLines = 1)
            }
        }
        // Pulso abajo
        Placed(left, top + f(0.845f), s, f(0.1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♥", fontSize = fs(0.08f), color = RedText)
                Spacer(Modifier.width(f(0.015f)))
                Text(if (heartRate > 0) "$heartRate" else "--", fontSize = fs(0.1f), fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }

        // Juegos por set y puntos de cada pareja
        Team.entries.forEach { t ->
            val gy = if (t == Team.A) 0.255f else 0.6f // fila de juegos
            val py = if (t == Team.A) 0.19f else 0.565f // puntos
            // Pelota de saque con D (derecha) o R (revés). Al empezar cada juego se
            // puede tocar para cambiar quién saca; con el primer punto se queda fija.
            if (saved.serverPicked && state.server == t && !showBigToss) {
                val drive = state.serverIsDrive(saved.firstDrive(t))
                val editable = state.atTurnStart
                Placed(left + f(0.355f), top + f(py + 0.05f), f(0.15f), f(0.15f)) {
                    Box(
                        Modifier.size(f(0.145f)).clip(CircleShape)
                            .then(if (editable) Modifier.background(Color.White).padding(f(0.01f)) else Modifier)
                            .then(if (editable) Modifier.clickable(onClick = onToggleServerPlayer) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        ServeBall(Modifier.fillMaxSize())
                        Text(
                            if (drive) "D" else "R",
                            fontSize = fs(0.075f),
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1B2433),
                        )
                    }
                }
            }
            Box(
                Modifier.offset(left + f(0.11f), top + f(gy)).size(f(0.3f), f(0.14f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(f(0.025f)), verticalAlignment = Alignment.CenterVertically) {
                    state.sets.forEach { set ->
                        val mine = if (t == Team.A) set.a else set.b
                        val won = set.winner == t
                        Text(
                            "$mine",
                            fontSize = fs(if (set.superTieBreak) 0.07f else 0.1f),
                            fontWeight = FontWeight.ExtraBold,
                            // Sets ya jugados más apagados que el set actual
                            color = if (won) Color(0xB3FFFFFF) else Color(0x66FFFFFF),
                        )
                    }
                    if (!state.superTieBreak || state.sets.isEmpty()) {
                        Text("${state.games(t)}", fontSize = fs(0.13f), fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
            if (!showBigToss) Placed(left + f(0.5f), top + f(py), f(0.42f), f(0.25f)) {
                // Antes de empezar: el primer toque en una mitad elige quién saca
                val label = if (saved.serverPicked) state.pointLabel(t) else "¿SACA?"
                Text(
                    label,
                    fontSize = fs(if (label.length > 3) 0.1f else if (label.length > 2) 0.2f else 0.26f),
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }

        // Franja central: deshacer, cronómetro + pulso, menú
        Box(Modifier.offset(left, top + f(0.5f) - 1.dp).size(s, 2.dp).background(Color(0x55FFFFFF)))
        Placed(left + f(0.015f), top + f(0.415f), f(0.17f), f(0.17f)) {
            RoundIconButton("↶", f(0.17f), fs(0.1f), enabled = saved.serverPicked, onClick = onUndo)
        }
        Placed(left + f(0.815f), top + f(0.415f), f(0.17f), f(0.17f)) {
            RoundIconButton("⋯", f(0.17f), fs(0.1f), enabled = true, onClick = onMenu)
        }
        if (showBigToss) {
            // Botón de sorteo gigante al empezar (60 % de la esfera)
            Placed(left + f(0.2f), top + f(0.2f), f(0.6f), f(0.6f)) {
                Column(
                    Modifier.fillMaxSize().clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFFE08A), Gold, Color(0xFFC9900C))))
                        .clickable(onClick = onToss),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(f(0.13f)).clip(CircleShape).background(Color(0xFFB8860B)),
                        contentAlignment = Alignment.Center,
                    ) { Text("€", fontSize = fs(0.08f), fontWeight = FontWeight.Bold, color = Gold) }
                    Spacer(Modifier.height(f(0.02f)))
                    Text("SORTEO", fontSize = fs(0.1f), fontWeight = FontWeight.ExtraBold, color = Color.Black)
                    Text("cara o cruz", fontSize = fs(0.05f), fontWeight = FontWeight.Bold, color = Color(0xAA000000))
                }
            }
        } else if (!saved.serverPicked) {
            // Botón de sorteo pequeño (ya se hizo el sorteo, falta tocar quién saca)
            Placed(left + f(0.2f), top + f(0.445f), f(0.6f), f(0.11f)) {
                Row(
                    Modifier.clip(RoundedCornerShape(f(0.055f))).background(Gold).clickable(onClick = onToss)
                        .padding(horizontal = f(0.04f), vertical = f(0.015f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(f(0.06f)).clip(CircleShape).background(Color(0xFFB8860B)),
                        contentAlignment = Alignment.Center,
                    ) { Text("€", fontSize = fs(0.04f), fontWeight = FontWeight.Bold, color = Gold) }
                    Spacer(Modifier.width(f(0.02f)))
                    Text("SORTEO", fontSize = fs(0.065f), fontWeight = FontWeight.ExtraBold, color = Color.Black)
                }
            }
        } else Placed(left + f(0.19f), top + f(0.44f), f(0.62f), f(0.12f)) {
            Row(
                Modifier.clip(RoundedCornerShape(f(0.045f))).background(if (deciding) Gold else Color(0xF00B1020))
                    .padding(horizontal = f(0.03f), vertical = f(0.008f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val timerColor = when {
                    deciding -> Color.Black
                    saved.timerRunning -> Gold
                    else -> Dim
                }
                Text("⏱", fontSize = fs(0.07f), color = timerColor)
                Spacer(Modifier.width(f(0.012f)))
                Text(formatDuration(saved.elapsedMs(now)), fontSize = fs(0.095f), fontWeight = FontWeight.ExtraBold, color = timerColor)
            }
        }
    }
}

/** Caja colocada en una posición absoluta (contenido centrado salvo que se indique otra cosa). */
@Composable
private fun Placed(
    x: Dp, y: Dp, w: Dp, h: Dp,
    align: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.offset(x, y).size(w, h), contentAlignment = align, content = content)
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
        val stroke = Stroke(width = w * 0.06f)
        val seam = Color.White.copy(alpha = 0.55f)
        drawCircle(Gold, radius = w / 2f)
        drawArc(
            seam, startAngle = -60f, sweepAngle = 120f, useCenter = false,
            topLeft = Offset(-w * 0.55f, w * 0.05f), size = Size(w * 0.9f, w * 0.9f), style = stroke,
        )
        drawArc(
            seam, startAngle = 120f, sweepAngle = 120f, useCenter = false,
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
private fun MenuScreen(saved: Saved, onChange: (Saved) -> Unit, onClose: () -> Unit, onToss: () -> Unit) {
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
                    onChange(saved.copy(history = emptyList(), timerStartedAt = 0L, timerAccumMs = 0L, serverChosen = false, endChangesSeen = 0))
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
        item { MenuChip("Sorteo con moneda", "Cara o cruz", onClick = onToss) }
        item {
            MenuChip("Saca primero: ${teamName(saved.firstServer)}", "Toca para cambiar") {
                onChange(saved.copy(firstServer = saved.firstServer.other, serverChosen = true))
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

/** Sorteo de campo o saque: una moneda que gira y cae en cara o cruz. */
@Composable
private fun CoinTossScreen(onClose: () -> Unit) {
    var tossId by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Boolean?>(null) } // true = cara
    val spin = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(tossId) {
        result = null
        val heads = Random.nextBoolean()
        spin.snapTo(0f)
        spin.animateTo(
            targetValue = 360f * 6 + if (heads) 0f else 180f,
            animationSpec = tween(1700, easing = FastOutSlowInEasing),
        )
        result = heads
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val s = if (maxWidth < maxHeight) maxWidth else maxHeight
        fun f(x: Float): Dp = s * x
        fun fs(x: Float): TextUnit = with(density) { (s * x).toSp() }
        val angle = spin.value % 360f
        val showingBack = angle > 90f && angle < 270f

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(f(0.56f))
                    .graphicsLayer {
                        rotationY = spin.value
                        cameraDistance = 14f * this.density
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (showingBack) listOf(Color(0xFFE0E0E0), Color(0xFF8E8E8E))
                            else listOf(Color(0xFFFFE08A), Color(0xFFC9900C))
                        )
                    )
                    .clickable(enabled = result != null) { tossId++ },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (showingBack) "CRUZ" else "CARA",
                    // La cara de atrás se dibuja girada: la volvemos a enderezar.
                    modifier = Modifier.graphicsLayer { scaleX = if (showingBack) -1f else 1f },
                    fontSize = fs(0.1f),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xCC3A2A00),
                )
            }
            Spacer(Modifier.height(f(0.015f)))
            val r = result
            Text(
                when (r) {
                    null -> "..."
                    true -> "¡CARA!"
                    false -> "¡CRUZ!"
                },
                fontSize = fs(0.1f),
                fontWeight = FontWeight.ExtraBold,
                color = if (r == null) Dim else Gold,
            )
            Spacer(Modifier.height(f(0.01f)))
            Row(horizontalArrangement = Arrangement.spacedBy(f(0.03f))) {
                Box(
                    Modifier.clip(RoundedCornerShape(f(0.05f))).background(ButtonDark)
                        .clickable(enabled = r != null) { tossId++ }
                        .padding(horizontal = f(0.04f), vertical = f(0.02f)),
                ) { Text("Repetir", fontSize = fs(0.055f), fontWeight = FontWeight.Bold, color = Color.White) }
                Box(
                    Modifier.clip(RoundedCornerShape(f(0.05f))).background(Gold)
                        .clickable(onClick = onClose)
                        .padding(horizontal = f(0.04f), vertical = f(0.02f)),
                ) { Text("Listo", fontSize = fs(0.055f), fontWeight = FontWeight.Bold, color = Color.Black) }
            }
        }
    }
}

/** Pantalla de "cambio de lado": tapa todo y se quita con un toque. */
@Composable
private fun ChangeEndsOverlay(tieBreak: Boolean, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xE6000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val s = if (maxWidth < maxHeight) maxWidth else maxHeight
        fun f(x: Float): Dp = s * x
        fun fs(x: Float): TextUnit = with(density) { (s * x).toSp() }
        Column(
            Modifier.size(f(0.66f)).clip(CircleShape).background(Color(0xFFF2F4F7)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("↔", fontSize = fs(0.16f), fontWeight = FontWeight.Bold, color = Color(0xFF0F5A40))
            Text("CAMBIO", fontSize = fs(0.085f), fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Text("DE LADO", fontSize = fs(0.085f), fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Spacer(Modifier.height(f(0.015f)))
            Text(
                if (tieBreak) "tie-break · toca para seguir" else "toca para seguir",
                fontSize = fs(0.042f), fontWeight = FontWeight.Bold, color = Color(0xFF5A6070),
            )
        }
    }
}
