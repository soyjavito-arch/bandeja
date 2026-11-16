package com.bandeja.movil

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandeja.shared.Dia
import com.bandeja.shared.Fechas
import com.bandeja.shared.Gym
import com.bandeja.shared.Kilos
import com.bandeja.shared.Pulso
import com.bandeja.shared.Rutina
import java.util.Calendar

private val SEMANA = listOf(
    Triple("lunes", "L", "Pádel"), Triple("martes", "M", "Empuje"), Triple("miercoles", "X", "Pádel"),
    Triple("jueves", "J", "Pierna"), Triple("viernes", "V", "Tirón"), Triple("sabado", "S", "Bici"),
    Triple("domingo", "D", "Pádel"),
)

private fun hoyId(): String = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> "lunes"; Calendar.TUESDAY -> "martes"; Calendar.WEDNESDAY -> "miercoles"
    Calendar.THURSDAY -> "jueves"; Calendar.FRIDAY -> "viernes"; Calendar.SATURDAY -> "sabado"; else -> "domingo"
}

@Composable
fun GymTab(v: Int, onIr: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = v
    val ses = Gym.sesion()
    var diaId by rememberSaveable { mutableStateOf(if (ses.inicio > 0 && ses.dia != null) ses.dia!! else hoyId()) }
    var ejIdx by rememberSaveable { mutableStateOf(-1) }
    val dia = Rutina.dia(diaId)
    val now = ahora()
    LaunchedEffect(now / 1000) { Gym.tick(now) }

    if (dia != null && ejIdx in dia.ejercicios.indices) {
        BackHandler { ejIdx = -1 }
        EjercicioPantalla(dia, ejIdx, ses, now, onIdx = { ejIdx = it }, onMenu = { ejIdx = -1 })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SEMANA.forEach { (id, letra, tipo) ->
                val sel = id == diaId
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) Azul else Tarjeta)
                        .clickable { diaId = id; ejIdx = -1 }.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(letra + if (id == hoyId()) " •" else "", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(tipo, color = if (sel) Color.White else Gris, fontSize = 11.sp)
                }
            }
        }
        if (dia == null) {
            val tipo = SEMANA.first { it.first == diaId }.third
            Column(Modifier.padding(16.dp)) {
                Titulo(tipo, if (tipo == "Bici") "Spinning con el Polar" else "Partido con el marcador del reloj")
                Pildora(if (tipo == "Bici") "Ir a Bici" else "Ir a Pádel", Azul) { onIr(if (tipo == "Bici") "bici" else "padel") }
            }
            return@Column
        }
        Box(Modifier.weight(1f)) {
            ListaDia(dia, ses, now, onEjercicio = { ejIdx = it })
        }
        val t = ses.timer
        if (t != null && ses.fin == 0L) {
            MiniDescanso(t, now, onAbrir = {
                val d = Rutina.dia(t.key.substringBefore('-'))
                if (d != null) { diaId = d.id; ejIdx = d.ejercicios.indexOfFirst { Gym.clave(d.id, it) == t.key } }
            })
        }
    }
}

@Composable
private fun ListaDia(dia: Dia, ses: Gym.Sesion, now: Long, onEjercicio: (Int) -> Unit) {
    var confirmar by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Titulo(dia.nombre, dia.sub)
        val activa = ses.inicio > 0 && ses.dia == dia.id
        if (activa) {
            Caja(color = Tarjeta2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (ses.fin > 0) "Sesión terminada" else "Sesión en marcha", color = Gris, fontSize = 13.sp)
                        Text(Fechas.hms(ses.duracionMs(now) / 1000), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Pulso.leer()?.let { (hr, kcal) -> Text("♥ $hr  ·  🔥 $kcal kcal", color = Gris, fontSize = 14.sp) }
                    }
                    if (ses.fin == 0L) Pildora("Terminar", Rojo) { confirmar = true }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        dia.ejercicios.forEachIndexed { i, ej ->
            val hechas = ses.hechas(Gym.clave(dia.id, ej)).size
            val completo = ej.numSeries > 0 && hechas >= ej.numSeries
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Tarjeta)
                    .clickable { onEjercicio(i) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Gif(ej.clave, Modifier.size(58.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ej.nombre, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${ej.series} · descanso ${ej.descanso}", color = Gris, fontSize = 13.sp)
                }
                if (ej.numSeries > 0) {
                    Text(if (completo) "✓" else "$hechas/${ej.numSeries}", color = if (completo) Verde else Gris,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Terminar la sesión?") },
            text = { Text("Se para el cronómetro. Lo apuntado se queda guardado.") },
            confirmButton = { TextButton({ Gym.terminarSesion(); confirmar = false }) { Text("Terminar") } },
            dismissButton = { TextButton({ confirmar = false }) { Text("Seguir") } },
        )
    }
}

/** Recuadro pequeño del descanso en el menú: tocarlo vuelve al ejercicio, con pausa y saltar. */
@Composable
private fun MiniDescanso(t: Gym.Temporizador, now: Long, onAbrir: () -> Unit) {
    val nombre = Rutina.dia(t.key.substringBefore('-'))?.let { d -> d.ejercicios.firstOrNull { Gym.clave(d.id, it) == t.key }?.nombre } ?: ""
    Row(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(16.dp)).background(Tarjeta2)
            .clickable(onClick = onAbrir).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (t.tipo == "trabajo") "SERIE EN MARCHA" else "DESCANSO", color = Naranja, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Text(Fechas.mmss((t.restanteMs(now) + 999) / 1000), color = if (t.terminado(now)) Verde else Color.White,
                fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            Text(nombre, color = Gris, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BotonRedondo(if (t.pausa) "▶" else "❚❚", 46.dp) { Gym.pausar() }
        Spacer(Modifier.width(8.dp))
        BotonRedondo("⏭", 46.dp) { Gym.saltar() }
    }
}

@Composable
private fun EjercicioPantalla(dia: Dia, idx: Int, ses: Gym.Sesion, now: Long, onIdx: (Int) -> Unit, onMenu: () -> Unit) {
    val ej = dia.ejercicios[idx]
    val key = Gym.clave(dia.id, ej)
    val hechas = ses.hechas(key)
    val vals = Gym.valores(dia.id, ej)
    val t = ses.timer
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        // ‹  ☰ Menú  ›   — moverse nunca cancela el descanso ni la sesión
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonRedondo("‹", 44.dp) { if (idx > 0) onIdx(idx - 1) }
            Spacer(Modifier.weight(1f))
            Pildora("☰  Menú", Tarjeta2, onClick = onMenu)
            Spacer(Modifier.weight(1f))
            BotonRedondo("›", 44.dp) { if (idx < dia.ejercicios.size - 1) onIdx(idx + 1) }
        }
        Text("${idx + 1} de ${dia.ejercicios.size}", color = Gris, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Gif(ej.clave, Modifier.fillMaxWidth().height(220.dp))
        Spacer(Modifier.height(10.dp))
        Text(ej.nombre, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Text("${ej.series} · descanso ${ej.descanso}", color = Gris, fontSize = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(ej.consejo, color = Color(0xFFCFCFD8), fontSize = 14.sp)
        Gym.anterior(dia.id, ej)?.let { (fecha, s) ->
            Spacer(Modifier.height(8.dp))
            Caja {
                Text("La semana pasada (${fecha.substring(8, 10)}/${fecha.substring(5, 7)})", color = Gris, fontSize = 13.sp)
                Text(s.joinToString("  ·  ") { "${it.first ?: "—"}×${Kilos.texto(it.second)}" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (t != null && ses.fin == 0L) {
            Spacer(Modifier.height(10.dp))
            DescansoGrande(t, now)
        }
        Gym.siguiente(dia, idx)?.let { si ->
            val sig = dia.ejercicios[si]
            Spacer(Modifier.height(10.dp))
            Caja(onClick = { onIdx(si) }) {
                Text("Siguiente ejercicio", color = Verde, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Gif(sig.clave, Modifier.size(48.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(sig.nombre, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(sig.series, color = Gris, fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (ej.numSeries == 0) {
            Text("Sin series que apuntar.", color = Gris, fontSize = 14.sp)
        }
        for (i in 0 until ej.numSeries) {
            val hecha = i in hechas
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (hecha) Color(0xFF16301F) else Tarjeta).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", color = Gris, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                if (ej.conPeso) {
                    val (r, kg) = vals.getOrElse(i) { null to null }
                    Paso("${r ?: "—"}", "reps", onMenos = { Gym.cambiar(dia.id, ej, i, ((r ?: 1) - 1).coerceAtLeast(0), kg) },
                        onMas = { Gym.cambiar(dia.id, ej, i, (r ?: 0) + 1, kg) })
                    Spacer(Modifier.weight(1f))
                    Paso(Kilos.texto(kg), "kg", onMenos = { Gym.cambiar(dia.id, ej, i, r, Kilos.bajar(kg)) },
                        onMas = { Gym.cambiar(dia.id, ej, i, r, Kilos.subir(kg)) })
                } else {
                    val trabajo = t?.takeIf { it.tipo == "trabajo" && it.key == key && it.serie == i }
                    Text(if (trabajo != null) Fechas.mmss((trabajo.restanteMs(now) + 999) / 1000) else Fechas.mmss(ej.trabajoSeg.toLong()),
                        color = if (trabajo != null) Naranja else Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (!hecha) {
                        if (trabajo == null) Pildora("▶ Empezar", Azul) { Gym.empezarTrabajo(dia, ej, i) }
                        else Pildora("Hecho", Verde) { Gym.saltar() }
                    }
                }
                Spacer(Modifier.width(8.dp))
                BotonRedondo("✓", 40.dp, color = if (hecha) Verde else Tarjeta2) { Gym.marcar(dia, ej, i, !hecha) }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DescansoGrande(t: Gym.Temporizador, now: Long) {
    val nombre = Rutina.dia(t.key.substringBefore('-'))?.let { d -> d.ejercicios.firstOrNull { Gym.clave(d.id, it) == t.key }?.nombre } ?: ""
    val fin = t.terminado(now)
    Caja(color = Tarjeta2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (t.tipo == "trabajo") "SERIE ${t.serie + 1} EN MARCHA" else if (fin) "¡A POR LA SIGUIENTE!" else "DESCANSO",
                    color = if (fin) Verde else Naranja, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(Fechas.mmss((t.restanteMs(now) + 999) / 1000), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                Text(nombre, color = Gris, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BotonRedondo(if (t.pausa) "▶" else "❚❚", 54.dp) { Gym.pausar() }
                Spacer(Modifier.height(8.dp))
                BotonRedondo("⏭", 54.dp) { Gym.saltar() }
            }
        }
    }
}
