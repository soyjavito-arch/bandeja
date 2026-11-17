package com.bandeja.padel

import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import com.bandeja.shared.Bici
import com.bandeja.shared.Dia
import com.bandeja.shared.Docs
import com.bandeja.shared.Ejercicio
import com.bandeja.shared.Fechas
import com.bandeja.shared.Gifs
import com.bandeja.shared.Gym
import com.bandeja.shared.Kilos
import com.bandeja.shared.Pulso
import com.bandeja.shared.Rutina
import com.bandeja.shared.Sync
import com.bandeja.shared.Zonas
import kotlinx.coroutines.delay
import org.json.JSONObject

// Letra de la web
private val Barlow = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(R.font.barlow_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.barlow_bold, FontWeight.Bold),
)

// Colores de cada entreno (los mismos que en el móvil)
private fun colorDia(id: String?) = when (id) { "martes" -> Color(0xFF2B5C9E); "jueves" -> Color(0xFFB4531C); else -> Color(0xFF7A3F9E) }
private fun colorDiaTxt(id: String?) = when (id) { "martes" -> Color(0xFF7AA6E6); "jueves" -> Color(0xFFF08A4B); else -> Color(0xFFC08AE6) }

// Colores del reloj
private val Naranja = Color(0xFFC25B1A)
private val NaranjaTxt = Color(0xFFFF9A4D)
private val Verde = Color(0xFF1D7A46)
private val VerdeTxt = Color(0xFF4FE3A3)
private val Rojo = Color(0xFFFF6B72)
private val Gris = Color(0xFF8A8F98)
private val Claro = Color(0xFFC4C8CE)
private val Boton = Color(0xFF26292E)
private val Fila = Color(0xFF1A1C20)
private val Azul = Color(0xFF16357F)
private val AzulBici = Color(0xFF5AA0FF)

@Composable
fun ahora(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    return now
}

@Composable
private fun Hora(now: Long, size: Int = 17) {
    Text(Fechas.hora(now), fontSize = (size + 4).sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Color.White)
}

/** Fila de abajo: tiempo de sesión, pulso y calorías. */
@Composable
private fun Datos(tiempo: String, hr: Int, kcal: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tiempo, fontSize = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text("♥" + (if (hr > 0) hr else "--"), fontSize = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Rojo)
        if (kcal != null) Text("🔥$kcal", fontSize = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = NaranjaTxt)
    }
}

@Composable
private fun Redondo(texto: String, onClick: () -> Unit, size: Int = 30, color: Color = Boton) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(texto, fontSize = (size * 0.6).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun Pastilla(texto: String, color: Color, onClick: () -> Unit, txt: Color = Color.White) {
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(color).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text(texto, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = txt)
    }
}

@Composable
fun GifView(clave: String, modifier: Modifier) {
    val ctx = LocalContext.current
    // Ejercicios sin imagen (p. ej. estiramientos): un icono en vez de un cuadrado blanco vacío.
    if (!remember(clave) { Gifs.existe(ctx, clave) }) {
        Box(modifier.background(Boton), contentAlignment = Alignment.Center) { Text("🤸", fontSize = 16.sp) }
        return
    }
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(android.graphics.Color.WHITE) } },
        update = { Gifs.poner(it, clave, fijo = true) },
        modifier = modifier,
    )
}

// =================== INICIO ===================

@Composable
fun InicioReloj(onPadel: () -> Unit, onGym: () -> Unit, onBici: () -> Unit) {
    val now = ahora()
    val conectado by Sync.conectado.collectAsState()
    LaunchedEffect(Unit) { Sync.comprobarConexion() }
    val bici by EntrenoService.biciActiva.collectAsState()
    val state = rememberScalingLazyListState()
    ScalingLazyColumn(Modifier.fillMaxSize().background(Color.Black), state = state) {
        item { Hora(now) }
        item { Text("ENTRENAR Y PÁDEL", fontSize = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold, color = Color.White) }
        item {
            Chip(onClick = onPadel, label = { Text("🎾 Pádel", fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text("Marcador") }, colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF2E7D4F)),
                modifier = Modifier.fillMaxWidth())
        }
        item {
            Chip(onClick = onGym, label = { Text("🏋️ Gym", fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text("Empuje · Pierna · Tirón") },
                colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF2B5C9E)), modifier = Modifier.fillMaxWidth())
        }
        item {
            Chip(onClick = onBici, label = { Text("🚲 Bici", fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text(if (bici) "En marcha" else "Spinning · Polar H10") },
                colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFFB4531C)), modifier = Modifier.fillMaxWidth())
        }
        item {
            Text(if (conectado) "● conectado con el móvil" else "○ sin móvil (se guarda igual)", fontSize = 11.sp,
                color = if (conectado) VerdeTxt else Gris)
        }
    }
}

// =================== GYM ===================

@Composable
fun GymReloj(onSalir: () -> Unit) {
    val ctx = LocalContext.current
    val v by Docs.version.collectAsState()
    val now = ahora()
    val hr by Pulsometro.bpm.collectAsState()
    LaunchedEffect(Unit) { Pulsometro.empezar(ctx) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Pulsometro.parar() } }
    LaunchedEffect(now) { Gym.tick(now) }

    @Suppress("UNUSED_VARIABLE") val ver = v
    val ses = Gym.sesion()
    var diaId by remember { mutableStateOf(ses.dia.takeIf { ses.inicio > 0 }) }
    var pantalla by remember { mutableStateOf(if (diaId == null) "dias" else if (ses.inicio > 0 && ses.fin == 0L) "serie" else "lista") }
    val dia = diaId?.let { Rutina.dia(it) }
    var idx by remember { mutableIntStateOf(dia?.let { Gym.actual(it) } ?: 0) }
    var verControles by remember { mutableStateOf(false) }

    val kcal = Pulso.leer()?.second
    val tiempo = Fechas.hms(ses.duracionMs(now) / 1000)

    fun empezarServicio() = EntrenoService.gym(ctx)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (dia == null || pantalla == "dias") {
            BackHandler { onSalir() }
            ElegirDia(now) { id ->
                diaId = id
                idx = Rutina.dia(id)?.let { Gym.actual(it) } ?: 0
                pantalla = "lista"
            }
            return@Box
        }
        val ej = dia.ejercicios[idx.coerceIn(0, dia.ejercicios.size - 1)]
        val key = Gym.clave(dia.id, ej)
        val hechas = ses.hechas(key)
        val t = ses.timer
        val descansando = t != null && t.tipo == "descanso" && !t.terminado(now) && t.key == key ||
            (t != null && t.tipo == "descanso" && !t.terminado(now) && pantalla == "serie")
        when {
            pantalla == "lista" -> {
                BackHandler { pantalla = "dias" }
                ListaDia(dia, ses, now, onEjercicio = { i -> idx = i; pantalla = "ficha" }, onTerminar = {
                    Gym.terminarSesion(); pantalla = "fin"
                }, onSalir = onSalir)
            }
            pantalla == "ficha" -> {
                BackHandler { pantalla = "lista" }
                Ficha(dia, ej, now) { empezarServicio(); pantalla = "serie" }
            }
            pantalla == "fin" -> {
                BackHandler { onSalir() }
                Fin(dia, ses, now, hr, kcal) { onSalir() }
            }
            descansando && t != null -> {
                BackHandler { pantalla = "lista" }
                val tdia = Rutina.dia(t.key.substringBefore('-')) ?: dia
                val tej = tdia.ejercicios.firstOrNull { Gym.clave(tdia.id, it) == t.key } ?: ej
                val tIdx = tdia.ejercicios.indexOf(tej)
                val vals = Gym.valores(tdia.id, tej)
                val sig = Gym.primeraPendiente(tdia, tej)
                val todas = ses.hechas(t.key).size >= tej.numSeries
                val luego = if (!todas && sig in vals.indices) "Luego: serie ${sig + 1} · ${vals[sig].first ?: "—"} × ${Kilos.texto(vals[sig].second)}" else null
                val siguiente = Gym.siguiente(tdia, tIdx)?.let { tdia.ejercicios[it].nombre }
                Descanso(tej.nombre, t.restanteMs(now), t.total, t.pausa, luego, siguiente, now, tiempo, hr, kcal,
                    verControles, onTap = { verControles = !verControles }, onPausa = { Gym.pausar() }, onSaltar = { Gym.saltar(); verControles = false })
            }
            ej.numSeries > 0 && hechas.size >= ej.numSeries -> {
                BackHandler { pantalla = "lista" }
                val sigIdx = Gym.siguiente(dia, idx)
                Siguiente(ej, sigIdx?.let { dia.ejercicios[it] }, now, tiempo, hr, kcal,
                    onEmpezar = { if (sigIdx != null) { idx = sigIdx } else { Gym.terminarSesion(); pantalla = "fin" } },
                    onLista = { pantalla = "lista" })
            }
            else -> {
                BackHandler { pantalla = "lista" }
                Serie(dia, ej, hechas, t?.takeIf { it.tipo == "trabajo" && it.key == key }, now, tiempo, hr, kcal,
                    onAnterior = { if (idx > 0) idx-- }, onSiguiente = { if (idx < dia.ejercicios.size - 1) idx++ },
                    onHecha = { s -> empezarServicio(); Gym.marcar(dia, ej, s) },
                    onTrabajo = { s -> empezarServicio(); Gym.empezarTrabajo(dia, ej, s) },
                    onSaltarTrabajo = { Gym.saltar() },
                    onLista = { pantalla = "lista" },
                    ultimo = Gym.siguiente(dia, idx) == null || idx >= dia.ejercicios.size - 1,
                    onTerminar = { Gym.terminarSesion(); pantalla = "fin" })
            }
        }
    }
}

@Composable
private fun ElegirDia(now: Long, onDia: (String) -> Unit) {
    val hechos = Gym.hechosCiclo()
    ScalingLazyColumn(Modifier.fillMaxSize()) {
        item { Hora(now) }
        item { Text("HECHOS ${hechos.size} DE ${Rutina.dias.size}", fontSize = 15.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Gris) }
        Rutina.dias.forEach { d ->
            val hecho = d.id in hechos
            val c = colorDia(d.id)
            item {
                Chip(onClick = { onDia(d.id) },
                    label = { Text((if (hecho) "✓ " else "") + d.tag, fontWeight = FontWeight.Bold) },
                    secondaryLabel = { Text(d.sub, maxLines = 1) },
                    colors = if (hecho) ChipDefaults.primaryChipColors(backgroundColor = c) else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ListaDia(dia: Dia, ses: Gym.Sesion, now: Long, onEjercicio: (Int) -> Unit, onTerminar: () -> Unit, onSalir: () -> Unit) {
    var confirmar by remember { mutableStateOf(false) }
    val actual = Gym.actual(dia)
    ScalingLazyColumn(Modifier.fillMaxSize()) {
        item { Hora(now) }
        item {
            Row {
                Text(dia.tag.uppercase(), fontSize = 19.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold, color = colorDiaTxt(dia.id))
                if (ses.inicio > 0) Text(" · " + Fechas.hms(ses.duracionMs(now) / 1000), fontSize = 19.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        dia.ejercicios.forEachIndexed { i, e ->
            item {
                val h = ses.hechas(Gym.clave(dia.id, e)).size
                val txt = when {
                    e.numSeries == 0 -> e.series
                    h >= e.numSeries -> "✓"
                    e.porTiempo && e.numSeries == 1 -> e.series
                    else -> "$h/${e.numSeries}"
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Fila)
                        .then(if (i == actual && ses.inicio > 0) Modifier.border(2.dp, colorDia(dia.id), RoundedCornerShape(24.dp)) else Modifier)
                        .clickable { onEjercicio(i) }.padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GifView(e.clave, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(9.dp))
                    Text(e.nombre, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(txt, fontSize = 24.sp, fontFamily = Barlow, color = if (txt == "✓") VerdeTxt else Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (ses.inicio > 0) item {
            Chip(onClick = { if (confirmar) onTerminar() else confirmar = true },
                label = { Text(if (confirmar) "¿Seguro? Toca otra vez" else "Terminar sesión") },
                secondaryLabel = { Text(Fechas.hms(ses.duracionMs(now) / 1000)) },
                colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
        }
        item { Chip(onClick = onSalir, label = { Text("Ir al inicio") }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun Ficha(dia: Dia, ej: Ejercicio, now: Long, onEmpezar: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Hora(now)
        GifView(ej.clave, Modifier.padding(top = 4.dp).size(118.dp, 84.dp).clip(RoundedCornerShape(10.dp)))
        Text(ej.nombre, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        val desc = if (ej.descanso != "—") " · descanso ${ej.descanso}" else ""
        Text(ej.series.replace("x", " × ") + desc, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Claro)
        Spacer(Modifier.height(5.dp))
        Pastilla("Empezar", Verde, onEmpezar)
    }
}

@Composable
private fun Serie(
    dia: Dia, ej: Ejercicio, hechas: Set<Int>, trabajo: Gym.Temporizador?, now: Long,
    tiempo: String, hr: Int, kcal: Int?,
    onAnterior: () -> Unit, onSiguiente: () -> Unit,
    onHecha: (Int) -> Unit, onTrabajo: (Int) -> Unit, onSaltarTrabajo: () -> Unit, onLista: () -> Unit,
    ultimo: Boolean = false, onTerminar: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val hayGif = remember(ej.clave) { Gifs.existe(ctx, ej.clave) }
    val vals = Gym.valores(dia.id, ej)
    val serie = Gym.primeraPendiente(dia, ej)
    // Todo va en una columna (también tiempo, pulso y kcal) para que nada se monte en la esfera redonda.
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Botón para volver a la lista del entreno (también vale el botón atrás del reloj).
            Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(colorDia(dia.id)).clickable(onClick = onLista)
                    .padding(horizontal = 9.dp, vertical = 1.dp)) {
                    Text("☰ Lista", fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(Fechas.hora(now), fontSize = 17.sp, lineHeight = 18.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Row(Modifier.height(26.dp).padding(horizontal = 34.dp).clickable(onClick = onLista), verticalAlignment = Alignment.CenterVertically) {
                if (hayGif) {
                    GifView(ej.clave, Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)))
                    Spacer(Modifier.width(5.dp))
                }
                Text(ej.nombre, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when {
                ej.numSeries == 0 -> {
                    Text(ej.series, fontSize = 14.sp, color = Claro, fontWeight = FontWeight.Bold)
                    Text(ej.consejo, fontSize = 11.sp, color = Claro, textAlign = TextAlign.Center, maxLines = 3,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp))
                    // En el último ejercicio (estiramientos) el botón cierra la sesión y va al resumen.
                    if (ultimo) Pastilla("Terminar sesión", Verde, onTerminar) else Pastilla("Siguiente", Verde, onSiguiente)
                }
                ej.porTiempo -> {
                    Text("SERIE ${serie + 1} DE ${ej.numSeries}", fontSize = 18.sp, lineHeight = 20.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold, color = colorDiaTxt(dia.id))
                    if (trabajo != null) {
                        Text(Fechas.mmss((trabajo.restanteMs(now) + 999) / 1000), fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = NaranjaTxt)
                        Pastilla("Hecha ya", Verde, onSaltarTrabajo)
                    } else {
                        Text(Fechas.mmss(ej.trabajoSeg.toLong()), fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Pastilla("Empezar", Color.White, { onTrabajo(serie) }, txt = Color.Black)
                    }
                }
                else -> {
                    Text("SERIE ${serie + 1} DE ${ej.numSeries}", fontSize = 18.sp, lineHeight = 20.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold, color = colorDiaTxt(dia.id))
                    val (r, w) = vals.getOrElse(serie) { null to null }
                    Fila(r?.toString() ?: "—", "reps", Color.White,
                        onMenos = { Gym.cambiar(dia.id, ej, serie, ((r ?: 1) - 1).coerceAtLeast(1), w) },
                        onMas = { Gym.cambiar(dia.id, ej, serie, ((r ?: 0) + 1).coerceAtMost(30), w) })
                    Fila(Kilos.texto(w), "kg", Color.White,
                        onMenos = { Gym.cambiar(dia.id, ej, serie, r, Kilos.bajar(w)) },
                        onMas = { Gym.cambiar(dia.id, ej, serie, r, Kilos.subir(w)) })
                    Spacer(Modifier.height(2.dp))
                    BotonTerminada(serie, onHecha)
                }
            }
            Spacer(Modifier.height(3.dp))
            DatosPeq(tiempo, hr, kcal)
        }
        Text("‹", fontSize = 26.sp, color = Claro, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp).clickable(onClick = onAnterior).padding(6.dp))
        Text("›", fontSize = 26.sp, color = Claro, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).clickable(onClick = onSiguiente).padding(6.dp))
    }
}

/** Blanco mientras la serie está pendiente; al pulsarlo se pone verde un momento y salta al descanso. */
@Composable
private fun BotonTerminada(serie: Int, onHecha: (Int) -> Unit) {
    var pulsado by remember(serie) { mutableStateOf(false) }
    LaunchedEffect(pulsado) {
        if (pulsado) { delay(350); onHecha(serie) }
    }
    Box(Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(if (pulsado) Verde else Color.White)
        .clickable(enabled = !pulsado) { pulsado = true }.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text("✓ Terminada", fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = if (pulsado) Color.White else Color.Black)
    }
}

/** Tiempo, pulso y calorías dentro del flujo de la pantalla (versión compacta). */
@Composable
private fun DatosPeq(tiempo: String, hr: Int, kcal: Int?) {
    Row(Modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tiempo, fontSize = 15.sp, lineHeight = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text("♥" + (if (hr > 0) hr else "--"), fontSize = 15.sp, lineHeight = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = Rojo)
        if (kcal != null) Text("🔥$kcal", fontSize = 15.sp, lineHeight = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, color = NaranjaTxt)
    }
}

@Composable
private fun Fila(valor: String, unidad: String, color: Color, onMenos: () -> Unit, onMas: () -> Unit) {
    Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Redondo("−", onMenos, 26)
        Row(Modifier.width(70.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            Text(valor, fontSize = 28.sp, lineHeight = 30.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold, color = color)
            Text(" $unidad", fontSize = 10.sp, color = Gris, modifier = Modifier.padding(bottom = 4.dp))
        }
        Redondo("+", onMas, 26)
    }
}

@Composable
private fun Descanso(
    nombre: String, restanteMs: Long, total: Int, pausa: Boolean, luego: String?, siguiente: String?,
    now: Long, tiempo: String, hr: Int, kcal: Int?, controles: Boolean,
    onTap: () -> Unit, onPausa: () -> Unit, onSaltar: () -> Unit,
) {
    val frac = if (total > 0) (restanteMs / 1000f / total).coerceIn(0f, 1f) else 0f
    Box(Modifier.fillMaxSize().clickable(onClick = onTap)) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val st = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Boton, 0f, 360f, false, style = st)
            drawArc(Color(0xFFE07B1F), -90f, 360f * frac, false, style = st)
        }
        Column(Modifier.fillMaxSize().padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Hora(now, 16)
            Text((if (pausa) "EN PAUSA · " else "DESCANSO · ") + nombre.uppercase(), fontSize = 15.sp, fontFamily = Barlow,
                fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 30.dp))
            Text(Fechas.mmss((restanteMs + 999) / 1000), fontSize = 76.sp, lineHeight = 72.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = if (pausa) 0.55f else 1f))
            if (controles) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Redondo(if (pausa) "▶" else "❚❚", onPausa, 36)
                    Redondo("»", onSaltar, 36)
                }
            } else {
                luego?.let { Text(it, fontSize = 12.sp, color = Claro) }
                siguiente?.let { Text("➜ $it", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerdeTxt, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 30.dp)) }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)) { Datos(tiempo, hr, kcal) }
    }
}

@Composable
private fun Siguiente(hecho: Ejercicio, sig: Ejercicio?, now: Long, tiempo: String, hr: Int, kcal: Int?, onEmpezar: () -> Unit, onLista: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Hora(now)
            Text("✓ ${hecho.nombre} hecha", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerdeTxt, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            if (sig != null) {
                Text("SIGUIENTE", fontSize = 10.sp, color = Gris, modifier = Modifier.padding(top = 3.dp).clickable(onClick = onLista))
                GifView(sig.clave, Modifier.size(60.dp, 44.dp).clip(RoundedCornerShape(8.dp)))
                Text(sig.nombre, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(3.dp))
                Pastilla("Empezar", Verde, onEmpezar)
            } else {
                Spacer(Modifier.height(16.dp))
                Pastilla("Terminar sesión", Verde, onEmpezar)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp)) { Datos(tiempo, hr, kcal) }
    }
}

@Composable
private fun Fin(dia: Dia, ses: Gym.Sesion, now: Long, hr: Int, kcal: Int?, onGuardar: () -> Unit) {
    val ej = dia.ejercicios.count { it.numSeries > 0 && ses.hechas(Gym.clave(dia.id, it)).size >= it.numSeries }
    val total = dia.ejercicios.count { it.numSeries > 0 }
    val series = ses.hechas.values.sumOf { it.size }
    val conectado by Sync.conectado.collectAsState()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Hora(now)
        Text("SESIÓN TERMINADA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = VerdeTxt)
        Text(Fechas.hms(ses.duracionMs(now) / 1000), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("🔥 ${kcal ?: "--"} kcal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NaranjaTxt)
        Text("$ej de $total ejercicios · $series series", fontSize = 12.sp, color = Claro)
        Spacer(Modifier.height(5.dp))
        Pastilla("Listo", Verde, onGuardar)
        Text(if (conectado) "● enviado al móvil" else "○ se enviará al conectar", fontSize = 10.sp, color = if (conectado) VerdeTxt else Gris)
    }
}

// =================== BICI ===================

@Composable
fun BiciReloj(onSalir: () -> Unit, pedirPermisos: (gps: Boolean, polar: Boolean, luego: () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    val activa by EntrenoService.biciActiva.collectAsState()
    val vivo by EntrenoService.vivo.collectAsState()
    BackHandler { onSalir() }
    if (!activa) {
        AjustesBici(onEmpezar = { polar ->
            pedirPermisos(Bici.ajustes().gps, polar) { EntrenoService.bici(ctx, polar) }
        }, onSalir = onSalir)
    } else {
        BiciEnMarcha(vivo, onPausa = { EntrenoService.biciPausa(ctx) }, onParar = { EntrenoService.biciParar(ctx) })
    }
}

@Composable
private fun AjustesBici(onEmpezar: (polar: Boolean) -> Unit, onSalir: () -> Unit) {
    val v by Docs.version.collectAsState()
    @Suppress("UNUSED_VARIABLE") val ver = v
    val a = Bici.ajustes()
    val now = ahora()
    ScalingLazyColumn(Modifier.fillMaxSize().background(Color.Black)) {
        item { Hora(now) }
        item {
            Chip(onClick = { onEmpezar(true) }, label = { Text("▶ Empezar con Polar", fontWeight = FontWeight.Bold) },
                secondaryLabel = { Text("H10 ${Bici.POLAR_ID}") },
                colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFFB4531C)), modifier = Modifier.fillMaxWidth())
        }
        item {
            Chip(onClick = { onEmpezar(false) }, label = { Text("▶ Con el pulso del reloj") },
                secondaryLabel = { Text("Si hoy no llevas el Polar") },
                colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
        }
        item {
            Chip(onClick = { Bici.guardar(a.copy(gps = !a.gps)) },
                label = { Text(if (a.gps) "🌳 Exterior · GPS" else "🏠 Spinning") }, secondaryLabel = { Text("Toca para cambiar") },
                colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
        }
        item {
            Chip(onClick = { Bici.guardar(a.copy(on = !a.on)) },
                label = { Text("⚡ Cambios de ritmo: " + if (a.on) "sí" else "no") },
                secondaryLabel = { Text(if (a.on) "${a.n} cambios · ${a.lo} → ${a.hi}" else "Start y Break por pulso") },
                colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
        }
        if (a.on) {
            item { Ajuste("Cambios", a.n.toString(), { Bici.guardar(a.copy(n = (a.n - 1).coerceAtLeast(1))) }, { Bici.guardar(a.copy(n = (a.n + 1).coerceAtMost(30))) }) }
            item { Ajuste("Start al bajar a", a.lo.toString(), { Bici.guardar(a.copy(lo = a.lo - 1)) }, { Bici.guardar(a.copy(lo = (a.lo + 1).coerceAtMost(a.hi - 1))) }) }
            item { Ajuste("Break al llegar a", a.hi.toString(), { Bici.guardar(a.copy(hi = (a.hi - 1).coerceAtLeast(a.lo + 1))) }, { Bici.guardar(a.copy(hi = a.hi + 1)) }) }
        }
        item { Chip(onClick = onSalir, label = { Text("Ir al inicio") }, colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun Ajuste(titulo: String, valor: String, onMenos: () -> Unit, onMas: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(titulo, fontSize = 11.sp, color = Gris)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Redondo("−", onMenos, 30)
            Text(valor, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
            Redondo("+", onMas, 30)
        }
    }
}

@Composable
private fun BiciEnMarcha(v: JSONObject?, onPausa: () -> Unit, onParar: () -> Unit) {
    val now = ahora()
    var controles by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf<Pair<String, String>?>(null) }
    var ultimo by remember { mutableStateOf("") }
    val hr = v?.optInt("hr") ?: 0
    val ritmo = v?.optJSONObject("ritmo")
    val estado = ritmo?.optString("estado") ?: ""
    val num = ritmo?.optInt("num") ?: 0
    val n = ritmo?.optInt("n") ?: 0
    // Pantalla grande de BREAK / FINISH unos segundos.
    LaunchedEffect("$estado-$num") {
        val firma = "$estado-$num"
        if (ultimo.isNotEmpty() && firma != ultimo && (estado == "recuperando" || estado == "fin")) {
            val h = ritmo?.optJSONArray("hechos")
            val ult = h?.optJSONArray((h.length() - 1).coerceAtLeast(0))?.optLong(0) ?: 0
            flash = (if (estado == "fin") "FINISH" else "BREAK") to "$num de $n · ${Fechas.mmss(ult)}"
            delay(3500)
            flash = null
        }
        ultimo = firma
    }
    val f = flash
    if (f != null) {
        Box(Modifier.fillMaxSize().background(if (f.first == "FINISH") Verde else Color(0xFFC0392B)).clickable { flash = null }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(f.first, fontSize = 42.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(f.second, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
        return
    }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable { controles = !controles; confirmar = false }) {
        Column(Modifier.fillMaxSize().padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Hora(now)
            val polar = v?.optString("fuente") == "polar"
            val gps = v != null && !v.isNull("km") && v.has("km")
            Text((if (gps) "EXTERIOR" else "SPINNING") + " · " + if (polar) "❤️ POLAR" else "⌚ RELOJ",
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AzulBici)
            Text("♥" + (if (hr > 0) hr else "--"), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Rojo)
            Text(Zonas.nombre(Zonas.zona(hr)).substringBefore(" ·"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerdeTxt)
            if (controles) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pastilla(if (v?.optBoolean("pausa") == true) "▶ Seguir" else "❚❚ Pausa", Boton, onPausa)
                    Pastilla(if (confirmar) "¿Seguro?" else "■ Fin", Color(0xFFC0392B), { if (confirmar) onParar() else confirmar = true })
                }
            } else if (ritmo != null) {
                val ms = ritmo.optLong("ms") / 1000
                val txt = when (estado) {
                    "esperando" -> "Start al bajar a ${ritmo.optInt("lo")}"
                    "subiendo" -> "▲ $num DE $n · ${Fechas.mmss(ms)}"
                    "recuperando" -> "Recupera · ${Fechas.mmss(ms)}"
                    else -> "✓ $n cambios hechos"
                }
                Text(txt, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (estado == "subiendo") NaranjaTxt else Claro)
                if (estado == "subiendo") Text("Break a ${ritmo.optInt("hi")}", fontSize = 11.sp, color = Gris)
            } else if (gps) {
                Text("%.1f km · %.0f km/h".format(v?.optDouble("km") ?: 0.0, v?.optDouble("kmh") ?: 0.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Claro)
            } else {
                Text("media ${v?.optInt("media") ?: 0}   máx ${v?.optInt("max") ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Claro)
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(Fechas.hms(v?.optLong("seg") ?: 0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("🔥${v?.optInt("kcal") ?: 0}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NaranjaTxt)
            if (v?.optBoolean("pausa") == true) Text("PAUSA", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NaranjaTxt)
        }
    }
}
