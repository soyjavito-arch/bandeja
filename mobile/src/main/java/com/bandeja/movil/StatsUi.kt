package com.bandeja.movil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandeja.shared.Docs
import com.bandeja.shared.Ejercicio
import com.bandeja.shared.Gym
import com.bandeja.shared.Kilos
import com.bandeja.shared.Rutina
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val SERIES = listOf(Color(0xFF2F7FE0), Color(0xFFE8673C), Color(0xFF1EA67A), Color(0xFFE6A10B), Color(0xFF8A5CD0))
private val MESES = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
private val corta = SimpleDateFormat("EEE dd/MM/yyyy", Locale("es", "ES"))
private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private fun ddmm(d: String) = if (d.length >= 10) "${d.substring(8, 10)}/${d.substring(5, 7)}" else d

@Composable
fun StatsPantalla(v: Int, onAtras: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = v
    var progDia by rememberSaveable { mutableStateOf(Rutina.dias.first().id) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Cabecera("Estadísticas", "‹", onAtras = onAtras)
        Calendario()
        Spacer(Modifier.height(12.dp))
        Caja {
            Text("PROGRESIÓN", color = Tinta, fontSize = 22.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Rutina.dias.forEach { d ->
                    val sel = d.id == progDia
                    val c = colorDia(d.id)
                    Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) c else Tarjeta)
                        .border(1.dp, if (sel) c else Linea, RoundedCornerShape(8.dp)).clickable { progDia = d.id },
                        contentAlignment = Alignment.Center) {
                        Text(d.tag.uppercase(), color = if (sel) Color.White else Tinta, fontSize = 18.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text("Peso y repeticiones de cada serie, cada una en su color. Toca un punto para ver los valores de ese día.", color = Gris, fontSize = 12.sp)
            val dia = Rutina.dia(progDia)
            dia?.ejercicios?.filter { it.conPeso }?.forEach { ej ->
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Linea)
                Progresion(dia.id, ej)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Calendario() {
    val hoy = Calendar.getInstance()
    var anio by rememberSaveable { mutableIntStateOf(hoy.get(Calendar.YEAR)) }
    var mes by rememberSaveable { mutableIntStateOf(hoy.get(Calendar.MONTH)) }
    var quitar by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Qué se hizo cada día: entrenos del gym, pádel y bici.
    val marcas = HashMap<String, MutableList<String>>()
    Gym.sesiones().forEach { (dia, fechas) -> fechas.forEach { marcas.getOrPut(it) { mutableListOf() }.add(dia) } }
    Docs.keys("padel-").forEach { marcas.getOrPut(it.removePrefix("padel-").take(10)) { mutableListOf() }.add("padel") }
    Docs.keys("bici-").forEach { marcas.getOrPut(it.removePrefix("bici-").take(10)) { mutableListOf() }.add("bici") }
    fun color(t: String) = when (t) { "padel" -> Verde; "bici" -> Rosa; else -> colorDia(t) }

    Caja {
        Text("DÍAS ENTRENADOS", color = Tinta, fontSize = 22.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonRedondo("‹", 36.dp) { if (mes == 0) { mes = 11; anio-- } else mes-- }
            Text("${MESES[mes].uppercase()} $anio", color = Tinta, fontSize = 20.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            BotonRedondo("›", 36.dp) { if (mes == 11) { mes = 0; anio++ } else mes++ }
        }
        Row(Modifier.fillMaxWidth()) {
            "LMXJVSD".forEach { Text("$it", color = Gris, fontSize = 12.sp, fontFamily = Barlow, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        val c = Calendar.getInstance().apply { set(anio, mes, 1) }
        val hueco = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val dias = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val hoyIso = iso.format(hoy.time)
        val cuenta = HashMap<String, Int>()
        val celdas = List(hueco) { 0 } + (1..dias).toList()
        celdas.chunked(7).forEach { semana ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0 until 7) {
                    val d = semana.getOrElse(k) { 0 }
                    if (d == 0) { Spacer(Modifier.weight(1f)); continue }
                    val f = "%04d-%02d-%02d".format(anio, mes + 1, d)
                    val m = marcas[f]
                    m?.forEach { cuenta[it] = (cuenta[it] ?: 0) + 1 }
                    val fondo = m?.firstOrNull()?.let { color(it) }
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(6.dp)).background(fondo ?: Fondo)
                            .border(if (f == hoyIso) 2.dp else 0.dp, if (f == hoyIso) Tinta else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable(enabled = m?.any { it in Rutina.dias.map { r -> r.id } } == true) {
                                quitar = m!!.first { it in Rutina.dias.map { r -> r.id } } to f
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$d", color = if (fondo != null) Color.White else Tinta, fontSize = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val leyenda = Rutina.dias.map { it.tag to it.id } + listOf("Pádel" to "padel", "Bici" to "bici")
        leyenda.chunked(3).forEach { fila ->
            Row(Modifier.padding(vertical = 2.dp)) {
                fila.forEach { (nombre, id) ->
                    Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(color(id)).align(Alignment.CenterVertically))
                    Text(" $nombre: ${cuenta[id] ?: 0}", color = Gris, fontSize = 13.sp, modifier = Modifier.padding(end = 14.dp))
                }
            }
        }
        Text("Toca un día marcado del gym para quitarlo si fue de prueba.", color = Gris, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
    quitar?.let { (dia, f) ->
        AlertDialog(
            onDismissRequest = { quitar = null },
            title = { Text("¿Quitar este día?") },
            text = { Text("${Rutina.dia(dia)?.tag ?: dia} del ${ddmm(f)} deja de contar como día entrenado. Las series apuntadas no se borran.") },
            confirmButton = { TextButton({ Gym.quitarSesion(dia, f); quitar = null }) { Text("Quitar", color = Rojo) } },
            dismissButton = { TextButton({ quitar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Progresion(diaId: String, ej: Ejercicio) {
    val hist = Docs.get(Gym.clave(diaId, ej))?.optJSONArray("hist")
    val dias = (0 until (hist?.length() ?: 0)).mapNotNull { hist!!.optJSONObject(it) }.map { h ->
        val s = h.optJSONArray("s")
        h.optString("d") to (0 until (s?.length() ?: 0)).map { i ->
            val r = s!!.optJSONArray(i)
            (if (r == null || r.isNull(0)) null else r.optInt(0)) to (if (r == null || r.isNull(1)) null else r.optDouble(1))
        }
    }
    Text(ej.nombre, color = Tinta, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    if (dias.isEmpty()) { Text("Sin datos todavía", color = Gris, fontSize = 12.sp); return }
    var sel by remember(diaId, ej.clave, dias.size) { mutableIntStateOf(dias.size - 1) }
    val n = dias.maxOf { it.second.size }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Grafico("Peso por serie (kg)", dias.map { d -> d.second.map { it.second } }, n, sel, dias, Modifier.weight(1f)) { sel = it }
        Grafico("Reps por serie", dias.map { d -> d.second.map { it.first?.toDouble() } }, n, sel, dias, Modifier.weight(1f)) { sel = it }
    }
    Row(Modifier.padding(top = 2.dp)) {
        for (k in 0 until n) {
            Box(Modifier.width(14.dp).height(3.dp).background(SERIES[k % SERIES.size]).align(Alignment.CenterVertically))
            Text(" Serie ${k + 1}  ", color = Gris, fontSize = 11.sp)
        }
    }
    val (f, vals) = dias[sel.coerceIn(0, dias.size - 1)]
    val fecha = runCatching { corta.format(iso.parse(f)!!) }.getOrDefault(f)
    Text("$fecha: " + vals.joinToString(" · ") { Kilos.texto(it.second) } + " kg | " + vals.joinToString(" · ") { "${it.first ?: "—"}" } + " reps",
        color = Tinta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun Grafico(titulo: String, datos: List<List<Double?>>, n: Int, sel: Int, dias: List<Pair<String, *>>, modifier: Modifier, onSel: (Int) -> Unit) {
    val todos = datos.flatten().filterNotNull()
    val paso = if (titulo.startsWith("Peso")) 2.5 else 2.5
    val lo = (todos.minOrNull() ?: 0.0) - paso
    val hi = (todos.maxOrNull() ?: 1.0) + paso
    fun fmt(x: Double) = Kilos.texto(Math.round(x * 2) / 2.0)
    Column(modifier) {
        Text(titulo, color = Gris, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row {
            Column(Modifier.width(30.dp).height(80.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(hi), color = Gris, fontSize = 9.sp); Text(fmt((hi + lo) / 2), color = Gris, fontSize = 9.sp); Text(fmt(lo), color = Gris, fontSize = 9.sp)
            }
            Canvas(Modifier.weight(1f).height(80.dp).pointerInput(datos.size) {
                detectTapGestures { p ->
                    if (datos.size <= 1) onSel(0)
                    else onSel(((p.x / size.width) * (datos.size - 1)).let { Math.round(it) }.coerceIn(0, datos.size - 1))
                }
            }) {
                val m = 6.dp.toPx()
                fun x(i: Int) = if (datos.size <= 1) size.width / 2 else m + (size.width - 2 * m) * i / (datos.size - 1)
                fun y(v: Double) = (m + (size.height - 2 * m) * (1 - (v - lo) / (hi - lo))).toFloat()
                listOf(0f, 0.5f, 1f).forEach { f ->
                    val yy = m + (size.height - 2 * m) * f
                    drawLine(Color(0xFFE3E8E6), Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1.5f)
                }
                drawLine(Gris.copy(alpha = 0.5f), Offset(x(sel), 0f), Offset(x(sel), size.height), strokeWidth = 2f)
                for (k in 0 until n) {
                    val c = SERIES[k % SERIES.size]
                    val p = Path(); var empezado = false
                    datos.forEachIndexed { i, d -> d.getOrNull(k)?.let { v -> if (!empezado) { p.moveTo(x(i), y(v)); empezado = true } else p.lineTo(x(i), y(v)) } }
                    drawPath(p, c, style = Stroke(width = 4f))
                    datos.forEachIndexed { i, d -> d.getOrNull(k)?.let { v ->
                        drawCircle(Tinta, 7f, Offset(x(i), y(v))); drawCircle(c, 5.5f, Offset(x(i), y(v)))
                    } }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 30.dp)) {
            Text(ddmm(dias.first().first), color = Gris, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            if (dias.size > 1) Text(ddmm(dias.last().first), color = Gris, fontSize = 9.sp)
        }
    }
}
