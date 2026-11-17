package com.bandeja.movil

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandeja.shared.Dia
import com.bandeja.shared.Docs
import com.bandeja.shared.Ejercicio
import com.bandeja.shared.Fechas
import com.bandeja.shared.Gym
import com.bandeja.shared.Kilos
import com.bandeja.shared.Pulso
import com.bandeja.shared.Rutina
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun GymTab(v: Int, onInicio: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = v
    val ses = Gym.sesion()
    var diaId by rememberSaveable { mutableStateOf(ses.dia ?: Rutina.dias.first().id) }
    var ejIdx by rememberSaveable { mutableIntStateOf(-1) }
    var stats by rememberSaveable { mutableStateOf(false) }
    val dia = Rutina.dia(diaId) ?: Rutina.dias.first()
    val now = ahora()
    LaunchedEffect(now / 1000) { Gym.tick(now) }

    if (stats) {
        BackHandler { stats = false }
        StatsPantalla(v, onAtras = { stats = false })
        return
    }
    if (ejIdx in dia.ejercicios.indices) {
        BackHandler { ejIdx = -1 }
        EjercicioPantalla(dia, ejIdx, ses, now, onIdx = { ejIdx = it }, onMenu = { ejIdx = -1 })
        return
    }

    val t = ses.timer?.takeIf { ses.fin == 0L }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Cabecera("Gym", onAtras = onInicio) { BotonRedondo("📈", 42.dp) { stats = true } }
            Pestanas(diaId) { diaId = it }
            val hechos = Gym.hechosCiclo()
            Text(
                if (hechos.size >= Rutina.dias.size) "¡Los tres hechos! El lunes vuelven a blanco."
                else "Hechos ${hechos.size} de ${Rutina.dias.size}. Al hacer los tres, el lunes siguiente vuelven a blanco.",
                color = Gris, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp),
            )
            if (ses.inicio > 0) {
                SesionCaja(ses, now, grande = true)
                Spacer(Modifier.height(4.dp))
            }
            dia.ejercicios.forEachIndexed { i, ej ->
                val hechas = ses.hechas(Gym.clave(dia.id, ej)).size
                val completo = ej.numSeries > 0 && hechas >= ej.numSeries
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Tarjeta)
                        .border(1.dp, Linea, RoundedCornerShape(10.dp)).clickable { ejIdx = i }.padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Gif(ej.clave, Modifier.size(54.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(ej.nombre, color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (ej.numSeries > 0) {
                        Text(if (completo) "✓" else "$hechas/${ej.numSeries}", color = if (completo) Verde else Gris,
                            fontSize = 20.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        if (t != null) {
            Tablero(t, now, Modifier.padding(10.dp), onIr = {
                val d = Rutina.dia(t.key.substringBefore('-'))
                if (d != null) { diaId = d.id; ejIdx = d.ejercicios.indexOfFirst { Gym.clave(d.id, it) == t.key } }
            })
        }
    }
}

/** Empuje / Pierna / Tirón: se rellenan con su color cuando ya están hechos en este ciclo. */
@Composable
fun Pestanas(sel: String, onSel: (String) -> Unit) {
    val hechos = Gym.hechosCiclo()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Rutina.dias.forEach { d ->
            val hecho = d.id in hechos
            val c = colorDia(d.id)
            val forma = RoundedCornerShape(8.dp)
            Box(
                Modifier.weight(1f).height(42.dp).clip(forma).background(if (hecho) c else Tarjeta)
                    .border(if (d.id == sel) 3.dp else 1.dp, if (d.id == sel) Tinta else Linea, forma)
                    .clickable { onSel(d.id) },
                contentAlignment = Alignment.Center,
            ) {
                Text((if (hecho) "✓ " else "") + d.tag.uppercase(), color = if (hecho) Color.White else Tinta,
                    fontSize = 18.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SesionCaja(ses: Gym.Sesion, now: Long, grande: Boolean, ejKey: String? = null, dia: Dia? = null, ej: Ejercicio? = null) {
    var confirmar by rememberSaveable { mutableStateOf(false) }
    var resetear by rememberSaveable { mutableStateOf(false) }
    Caja(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // Un toque en el tiempo: pausa / sigue. Dos toques: empezar de cero (pide confirmación).
                Column(Modifier.pointerInput(ses.fin == 0L) {
                    detectTapGestures(
                        onTap = { if (Gym.sesion().fin == 0L) Gym.pausarSesion() },
                        onDoubleTap = { resetear = true },
                    )
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ses.fin > 0) Verde else if (ses.pausada) Naranja else Rojo))
                        Text(if (ses.fin > 0) "  Sesión terminada" else if (ses.pausada) "  Sesión en pausa" else "  Sesión en marcha",
                            color = if (ses.pausada) Naranja else Gris, fontSize = 13.sp)
                    }
                    Text(Fechas.hms(ses.duracionMs(now) / 1000), color = if (ses.pausada) Naranja else Tinta,
                        fontSize = if (grande) 50.sp else 36.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                }
                if (grande) Pulso.leer()?.let { (hr, kcal) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("♥ $hr", color = Rojo, fontSize = 24.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                        Text("    🔥 $kcal kcal", color = Naranja, fontSize = 24.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Interruptor: blanco «En marcha» ↔ verde «Terminada». Si se terminó por error, se toca y sigue.
            if (grande) {
                val hecha = ses.fin > 0
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (hecha) Verde else Color.White)
                    .border(1.5.dp, if (hecha) Verde else Linea, RoundedCornerShape(50))
                    .clickable { if (hecha) Gym.reanudarSesion() else confirmar = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(if (hecha) "✓ Terminada" else "● En marcha", color = if (hecha) Color.White else Tinta,
                        fontSize = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                }
            }
            if (!grande) {
                // Cronómetro del ejercicio: arranca al entrar y se para con la última serie.
                // Se puede parar, seguir o volver a empezar a mano.
                val ms = ejKey?.let { ses.tiempoEjMs(it, now) }
                val parado = ejKey != null && (ses.tiempos[ejKey]?.second ?: 0L) > 0
                val completo = ejKey != null && ej != null && ej.numSeries > 0 && ses.hechas(ejKey).size >= ej.numSeries
                val color = if (completo && parado) Verde else if (parado) Naranja else Tinta
                // Un toque: para / arranca. Dos toques: vuelve a cero y arranca.
                val manejable = dia != null && ej != null && ej.numSeries > 0
                Column(Modifier.weight(1f).then(if (manejable) Modifier.pointerInput(ejKey) {
                    detectTapGestures(
                        onTap = { Gym.cronoEjercicio(dia!!, ej!!, "tocar") },
                        onDoubleTap = { Gym.cronoEjercicio(dia!!, ej!!, "iniciar") },
                    )
                } else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (completo && parado) "Ejercicio ✓" else if (parado) "Ejercicio · parado" else "Ejercicio", color = if (parado) color else Gris, fontSize = 13.sp)
                    Text(ms?.let { Fechas.mmss(it / 1000) } ?: "--:--", color = color, fontSize = 36.sp,
                        fontFamily = Barlow, fontWeight = FontWeight.Bold)
                }
                Pulso.leer()?.let { (hr, kcal) ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text("♥ $hr", color = Rojo, fontSize = 24.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                        Text("🔥 $kcal", color = Naranja, fontSize = 24.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (resetear) {
        val nombre = ses.dia?.let { Rutina.dia(it)?.tag } ?: "la sesión"
        AlertDialog(
            onDismissRequest = { resetear = false },
            title = { Text("¿Empezar de cero?") },
            text = { Text("Se borra la sesión de hoy: $nombre vuelve a blanco, el tiempo a cero y las series sin marcar. Los kg y reps que pusiste se quedan.") },
            confirmButton = { TextButton({ Gym.resetearSesion(); resetear = false }) { Text("Empezar de cero", color = Rojo) } },
            dismissButton = { TextButton({ resetear = false }) { Text("Cancelar") } },
        )
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Terminar la sesión?") },
            text = { Text("Se para el cronómetro. Lo apuntado se queda guardado. Si te equivocas, toca «Terminada» y sigue.") },
            confirmButton = { TextButton({ Gym.terminarSesion(); confirmar = false }) { Text("Terminar") } },
            dismissButton = { TextButton({ confirmar = false }) { Text("Seguir") } },
        )
    }
}

/** El descanso grande y oscuro de la web: tocar = pausa, ✕ = quitarlo. */
@Composable
private fun Tablero(t: Gym.Temporizador, now: Long, modifier: Modifier = Modifier, grande: Boolean = false, onIr: (() -> Unit)? = null) {
    val d = Rutina.dia(t.key.substringBefore('-'))
    val nombre = d?.ejercicios?.firstOrNull { Gym.clave(d.id, it) == t.key }?.nombre ?: ""
    val fin = t.terminado(now)
    val trabajo = t.tipo == "trabajo"
    val forma = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxWidth().clip(forma).background(if (trabajo) colorDia(d?.id) else Tinta).clickable { Gym.pausar() }
        .padding(horizontal = 14.dp, vertical = 10.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val lbl = (if (fin && !trabajo) "¡A por la siguiente!" else if (trabajo) "Tiempo · Serie ${t.serie + 1}" else "Descanso · Serie ${t.serie + 1}") +
                if (onIr != null) " · $nombre" else ""
            Text(lbl.uppercase(), color = if (fin) Color(0xFF7BE0A4) else Color.White.copy(alpha = 0.8f), fontSize = 16.sp,
                fontFamily = Barlow, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 40.dp))
            Text(Fechas.mmss((t.restanteMs(now) + 999) / 1000), color = Color.White.copy(alpha = if (t.pausa) 0.55f else 1f),
                fontSize = if (grande) 120.sp else 96.sp, lineHeight = if (grande) 120.sp else 96.sp,
                fontFamily = Barlow, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (t.pausa) "En pausa · toca para seguir" else "Toca para pausar", color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = if (onIr == null) TextAlign.Center else TextAlign.Start)
                if (onIr != null) {
                    Text("Ir al ejercicio ›", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable(onClick = onIr).padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).size(34.dp).clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).clickable { Gym.quitarTemporizador() },
            contentAlignment = Alignment.Center) {
            Text("✕", color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun EjercicioPantalla(dia: Dia, idx: Int, ses: Gym.Sesion, now: Long, onIdx: (Int) -> Unit, onMenu: () -> Unit) {
    val ej = dia.ejercicios[idx]
    val t = ses.timer?.takeIf { ses.fin == 0L }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Spacer(Modifier.height(6.dp))
        val ejKey = Gym.clave(dia.id, ej)
        LaunchedEffect(ejKey) { Gym.entrarEjercicio(dia, ej) }
        if (ses.inicio > 0 || ejKey in ses.tiempos || ej.numSeries > 0) SesionCaja(ses, now, grande = false, ejKey = ejKey, dia = dia, ej = ej)
        // ‹  ☰ Menú  ›   — moverse nunca cancela el descanso ni la sesión
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonRedondo("‹", 44.dp) { if (idx > 0) onIdx(idx - 1) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Pildora("☰  Menú", Tarjeta, onClick = onMenu)
                Text("${idx + 1} de ${dia.ejercicios.size}", color = Gris, fontSize = 12.sp)
            }
            BotonRedondo("›", 44.dp) { if (idx < dia.ejercicios.size - 1) onIdx(idx + 1) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Gif(ej.clave, Modifier.size(96.dp))
            Spacer(Modifier.width(12.dp))
            Text(ej.nombre.uppercase(), color = Tinta, fontSize = 26.sp, lineHeight = 27.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        }
        if (t != null) Tablero(t, now, Modifier.padding(vertical = 10.dp), grande = true)
        else Spacer(Modifier.height(10.dp))
        if (ej.numSeries > 0) Tabla(dia, ej, ses, now)
        Gym.siguiente(dia, idx)?.let { si ->
            val sig = dia.ejercicios[si]
            Spacer(Modifier.height(10.dp))
            Caja(onClick = { onIdx(si) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gif(sig.clave, Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Siguiente ejercicio", color = Verde, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(sig.nombre, color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private val diaWeb = SimpleDateFormat("EEE dd/MM/yyyy", Locale("es", "ES"))
private val diaIso = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** Tabla de la web: REPS · KG · DESCANSO · SERIE. */
@Composable
private fun Tabla(dia: Dia, ej: Ejercicio, ses: Gym.Sesion, now: Long) {
    val key = Gym.clave(dia.id, ej)
    val hechas = ses.hechas(key)
    val vals = Gym.valores(dia.id, ej)
    val t = ses.timer?.takeIf { ses.fin == 0L }
    val c = colorDia(dia.id)
    val conDescanso = ej.descansoSeg > 0
    Caja {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(16.dp))
            if (ej.conPeso) { Cab("Reps", Modifier.weight(1f)); Cab("Kg", Modifier.weight(1f)) } else Cab("Tiempo", Modifier.weight(2f))
            if (conDescanso) Cab("Desc.", Modifier.width(54.dp))
            Cab("Serie", Modifier.width(78.dp))
        }
        for (i in 0 until ej.numSeries) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${i + 1}", color = c, fontSize = 18.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold, modifier = Modifier.width(12.dp))
                if (ej.conPeso) {
                    val (r, kg) = vals.getOrElse(i) { null to null }
                    MiniPaso("${r ?: "—"}", Modifier.weight(1f),
                        onMenos = { Gym.cambiar(dia.id, ej, i, ((r ?: 1) - 1).coerceAtLeast(0), kg) },
                        onMas = { Gym.cambiar(dia.id, ej, i, (r ?: 0) + 1, kg) })
                    MiniPaso(Kilos.texto(kg), Modifier.weight(1f),
                        onMenos = { Gym.cambiar(dia.id, ej, i, r, Kilos.bajar(kg)) },
                        onMas = { Gym.cambiar(dia.id, ej, i, r, Kilos.subir(kg)) })
                } else {
                    val tr = t?.takeIf { it.tipo == "trabajo" && it.key == key && it.serie == i }
                    Celda(if (tr != null) Fechas.mmss((tr.restanteMs(now) + 999) / 1000) else Fechas.mmss(ej.trabajoSeg.toLong()),
                        Modifier.weight(2f), Tinta, lleno = tr != null) {
                        if (tr != null) Gym.pausar() else if (i !in hechas) Gym.empezarTrabajo(dia, ej, i)
                    }
                }
                if (conDescanso) {
                    val de = t?.takeIf { it.tipo == "descanso" && it.key == key && it.serie == i }
                    Celda(if (de != null) Fechas.mmss((de.restanteMs(now) + 999) / 1000) else Fechas.mmss(ej.descansoSeg.toLong()),
                        Modifier.width(54.dp), c, lleno = de != null) {
                        if (de != null) Gym.pausar() else Gym.empezarDescanso(dia, ej, i)
                    }
                }
                val hecha = i in hechas
                Box(
                    Modifier.width(78.dp).height(40.dp).clip(RoundedCornerShape(6.dp)).background(if (hecha) Verde else Tarjeta)
                        .border(1.dp, if (hecha) Verde else Linea, RoundedCornerShape(6.dp)).clickable { Gym.marcar(dia, ej, i, !hecha) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (hecha) "Terminada" else "Pendiente", color = if (hecha) Color.White else Gris, fontSize = 16.sp,
                        fontFamily = Barlow, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        val hist = Docs.get(key)?.optJSONArray("hist")
        val ult = hist?.optJSONObject(hist.length() - 1)?.optString("d")
        val txt = ult?.let { runCatching { diaWeb.format(diaIso.parse(it)!!) }.getOrNull() }
        Text(if (txt != null) "Actualizado: $txt" else "Sin datos todavía", color = Gris, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Cab(t: String, modifier: Modifier) {
    Text(t.uppercase(), color = Gris, fontSize = 13.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

/** Casilla de reps o kg: la mitad izquierda resta y la derecha suma; el número siempre entero en el centro. */
@Composable
private fun MiniPaso(valor: String, modifier: Modifier, onMenos: () -> Unit, onMas: () -> Unit) {
    val forma = RoundedCornerShape(6.dp)
    Box(modifier.height(40.dp).clip(forma).background(Fondo).border(1.dp, Linea, forma)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onMenos).padding(start = 6.dp), contentAlignment = Alignment.CenterStart) {
                Text("−", color = Gris, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onMas).padding(end = 6.dp), contentAlignment = Alignment.CenterEnd) {
                Text("+", color = Gris, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(valor, color = Tinta, fontSize = 20.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun Celda(texto: String, modifier: Modifier, c: Color, lleno: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(6.dp)
    Box(modifier.height(38.dp).clip(forma).background(if (lleno) c else Tarjeta).border(1.dp, c, forma).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(texto, color = if (lleno) Color.White else c, fontSize = 18.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold)
    }
}
