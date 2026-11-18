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
    // El día elegido en el calendario manda en todas las pestañas.
    var sel by rememberSaveable { mutableStateOf(iso.format(java.util.Date())) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Cabecera("Estadísticas", "‹", onAtras = onAtras)
        Calendario(sel) { sel = it }
        Spacer(Modifier.height(12.dp))
        // Gym, Pádel o Bici: cada uno con sus números y gráficas.
        var vista by rememberSaveable { mutableStateOf("total") }
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Triple("total", "TOTAL", Tinta), Triple("gym", "GYM", Azul), Triple("padel", "PÁDEL", Verde), Triple("bici", "BICI", Rosa)).forEach { (id, t, c) ->
                val sel = vista == id
                Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(10.dp)).background(if (sel) c else Tarjeta)
                    .border(1.dp, if (sel) c else Linea, RoundedCornerShape(10.dp)).clickable { vista = id },
                    contentAlignment = Alignment.Center) {
                    Text(t, color = if (sel) Color.White else Tinta, fontSize = 18.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (vista == "total") StatsTotal(sel) { sel = it }
        if (vista == "padel") StatsPadel(sel)
        if (vista == "bici") StatsBici(sel)
        if (vista == "gym") StatsGymDia(sel)
        if (vista == "gym") Caja {
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
private fun Calendario(sel: String, onSel: (String) -> Unit) {
    val hoy = Calendar.getInstance()
    // El mes que se ve sigue al día elegido; las flechas solo pasan de mes.
    var anio by rememberSaveable(sel) { mutableIntStateOf(sel.take(4).toIntOrNull() ?: hoy.get(Calendar.YEAR)) }
    var mes by rememberSaveable(sel) { mutableIntStateOf((sel.substring(5, 7).toIntOrNull() ?: (hoy.get(Calendar.MONTH) + 1)) - 1) }
    var quitar by remember { mutableStateOf<String?>(null) } // día con el diálogo de borrar abierto

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
                    val elegido = f == sel
                    Box(
                        Modifier.weight(1f).height(40.dp)
                            .border(if (elegido) 3.dp else 0.dp, if (elegido) Naranja else Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(if (elegido) 4.dp else 0.dp)
                            .clip(RoundedCornerShape(6.dp)).background(fondo ?: Fondo)
                            .border(if (f == hoyIso) 2.dp else 0.dp, if (f == hoyIso) Tinta else Color.Transparent, RoundedCornerShape(6.dp))
                            .pointerInput(f, m?.joinToString()) {
                                detectTapGestures(
                                    onTap = { onSel(f) },
                                    onLongPress = { if (m != null) quitar = f },
                                )
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
    }
    // Mantener pulsado un día: borrar lo que se hizo (gym, pádel o bici), uno a uno.
    var borrados by remember { mutableIntStateOf(0) }
    quitar?.let { f ->
        if (borrados < 0) return@let // leerlo hace que la lista se rehaga al borrar
        val cosas = ArrayList<Pair<String, () -> Unit>>()
        Gym.sesiones().forEach { (dia, fechas) ->
            if (f in fechas) cosas += "${Rutina.dia(dia)?.tag ?: dia}" to { Gym.quitarSesion(dia, f); Docs.delete("gym-$f-$dia") }
        }
        Docs.keys("padel-$f").sorted().forEach { k -> cosas += "Pádel ${k.removePrefix("padel-$f-").let { "${it.take(2)}:${it.drop(2)}" }}" to { Docs.delete(k) } }
        Docs.keys("bici-$f").sorted().forEach { k -> cosas += "Bici ${k.removePrefix("bici-$f-").let { "${it.take(2)}:${it.drop(2)}" }}" to { Docs.delete(k) } }
        if (cosas.isEmpty()) { quitar = null; return@let }
        AlertDialog(
            onDismissRequest = { quitar = null },
            title = { Text("Borrar del ${ddmm(f)}") },
            text = {
                Column {
                    cosas.forEach { (nombre, borrar) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(nombre, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton({ borrar(); borrados++ }) { Text("Borrar", color = Rojo, fontSize = 16.sp) }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ quitar = null }) { Text("Listo") } },
        )
    }
}

@Composable
private fun Progresion(diaId: String, ej: Ejercicio) {
    val hist = Docs.get(Gym.clave(diaId, ej))?.optJSONArray("hist")
    // Solo los días que cuentan como entrenados (un día quitado en el calendario no sale).
    val validos = Gym.sesiones()[diaId]?.toSet() ?: emptySet()
    val dias = (0 until (hist?.length() ?: 0)).mapNotNull { hist!!.optJSONObject(it) }.filter { it.optString("d") in validos }.map { h ->
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


// =================== PÁDEL Y BICI ===================

/** Una cifra grande con su etiqueta. */
@Composable
private fun Cifra(valor: String, etiqueta: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, color = color, fontSize = 30.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        Text(etiqueta, color = Gris, fontSize = 12.sp)
    }
}

@Composable
private fun FilaCifras(vararg c: Triple<String, String, Color>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { c.forEach { Cifra(it.first, it.second, it.third, Modifier.weight(1f)) } }
}

private fun horas(seg: Long) = if (seg >= 3600) "${seg / 3600}h ${"%02d".format((seg % 3600) / 60)}" else "${seg / 60} min"

/** El mes del día elegido y desde siempre, con el mismo formato. */
@Composable
private fun <T> Periodos(sel: String, todos: List<T>, fecha: (T) -> String, contenido: @Composable (List<T>) -> Unit) {
    val mes = sel.take(7)
    Text("MES DE ${MESES[(sel.substring(5, 7).toIntOrNull() ?: 1) - 1].uppercase()}", color = Gris, fontSize = 14.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
    contenido(todos.filter { fecha(it).startsWith(mes) })
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Linea)
    Text("DESDE SIEMPRE", color = Gris, fontSize = 14.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
    contenido(todos)
}

@Composable
private fun StatsPadel(sel: String) {
    val ps = com.bandeja.shared.Partidos.todos().sortedBy { it.inicio }
    Caja {
        Text("PÁDEL", color = Tinta, fontSize = 22.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        if (ps.isEmpty()) { Text("Todavía no hay partidos.", color = Gris, fontSize = 14.sp); return@Caja }
        DiaElegido(sel, "padel")
        Periodos(sel, ps, { com.bandeja.shared.Fechas.dia(it.inicio) }) { l ->
            val gan = l.count { it.ganador == "azul" }
            val per = l.count { it.ganador == "verde" }
            FilaCifras(
                Triple("${l.size}", "partidos", Tinta),
                Triple("$gan-$per", "ganados-perdidos", Azul),
                Triple(if (gan + per > 0) "${gan * 100 / (gan + per)}%" else "—", "victorias", Verde),
            )
            FilaCifras(
                Triple(horas(l.sumOf { it.dur }), "jugadas", Tinta),
                Triple("${l.mapNotNull { it.kcal }.sum()}", "kcal", Naranja),
                Triple(l.mapNotNull { it.pm }.takeIf { it.isNotEmpty() }?.average()?.toInt()?.toString() ?: "—", "pulso medio", Rojo),
            )
        }
        // Últimos 20 partidos: kcal y pulso medio de cada uno.
        val ult = ps.takeLast(20)
        if (ult.size >= 2) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Linea)
            var sel by remember(ult.size) { mutableIntStateOf(ult.size - 1) }
            val dias = ult.map { com.bandeja.shared.Fechas.dia(it.inicio) to it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Grafico("Kcal por partido", ult.map { listOf(it.kcal?.toDouble()) }, 1, sel, dias, Modifier.weight(1f)) { sel = it }
                Grafico("Pulso medio", ult.map { listOf(it.pm?.toDouble()) }, 1, sel, dias, Modifier.weight(1f)) { sel = it }
            }
            val p = ult[sel.coerceIn(0, ult.size - 1)]
            Text("${ddmm(com.bandeja.shared.Fechas.dia(p.inicio))}: " + p.sets.joinToString(" ") { "${it.first}-${it.second}" } +
                " · ${horas(p.dur)}" + (p.kcal?.let { " · 🔥 $it" } ?: "") + (p.pm?.let { " · ♥ $it" } ?: ""),
                color = Tinta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private data class SesionBici(val fecha: String, val dur: Long, val kcal: Int, val pm: Int?, val pmax: Int?, val subida: Double?, val rec: Double?)

@Composable
private fun StatsBici(sel: String) {
    val ss = Docs.keys("bici-").sorted().mapNotNull { k ->
        val d = Docs.get(k) ?: return@mapNotNull null
        val c = d.optJSONArray("cambios")
        val cam = (0 until (c?.length() ?: 0)).mapNotNull { c!!.optJSONArray(it) }
        SesionBici(d.optString("fecha").ifEmpty { k.removePrefix("bici-").take(10) }, d.optLong("dur"), d.optInt("kcal"),
            if (d.isNull("pm")) null else d.optInt("pm"), if (d.isNull("pmax")) null else d.optInt("pmax"),
            cam.map { it.optLong(0) }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average(),
            cam.map { it.optLong(1) }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average())
    }
    Caja {
        Text("BICI", color = Tinta, fontSize = 22.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        if (ss.isEmpty()) { Text("Todavía no hay sesiones de bici.", color = Gris, fontSize = 14.sp); return@Caja }
        DiaElegido(sel, "bici")
        Periodos(sel, ss, { it.fecha }) { l ->
            FilaCifras(
                Triple("${l.size}", "sesiones", Tinta),
                Triple(horas(l.sumOf { it.dur }), "pedaleadas", Tinta),
                Triple("${l.sumOf { it.kcal }}", "kcal", Naranja),
            )
            FilaCifras(
                Triple(l.mapNotNull { it.pm }.takeIf { it.isNotEmpty() }?.average()?.toInt()?.toString() ?: "—", "pulso medio", Rojo),
                Triple(l.mapNotNull { it.pmax }.maxOrNull()?.toString() ?: "—", "pulso máx", Rojo),
                Triple(if (l.isNotEmpty()) "${l.sumOf { it.dur } / l.size / 60} min" else "—", "por sesión", Tinta),
            )
        }
        val ult = ss.takeLast(20)
        if (ult.size >= 2) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Linea)
            var sel by remember(ult.size) { mutableIntStateOf(ult.size - 1) }
            val dias = ult.map { it.fecha to it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Grafico("Minutos", ult.map { listOf(it.dur / 60.0) }, 1, sel, dias, Modifier.weight(1f)) { sel = it }
                Grafico("Pulso medio", ult.map { listOf(it.pm?.toDouble()) }, 1, sel, dias, Modifier.weight(1f)) { sel = it }
            }
            // Cambios de ritmo: si tardas menos en recuperar, estás más en forma.
            if (ult.any { it.subida != null }) {
                Spacer(Modifier.height(8.dp))
                Grafico("Cambios de ritmo: subida y recuperación (seg)", ult.map { listOf(it.subida, it.rec) }, 2, sel, dias, Modifier.fillMaxWidth()) { sel = it }
                Row {
                    Box(Modifier.width(14.dp).height(3.dp).background(SERIES[0]).align(Alignment.CenterVertically)); Text(" Subida  ", color = Gris, fontSize = 11.sp)
                    Box(Modifier.width(14.dp).height(3.dp).background(SERIES[1]).align(Alignment.CenterVertically)); Text(" Recuperación (menos = más en forma)", color = Gris, fontSize = 11.sp)
                }
            }
            val b = ult[sel.coerceIn(0, ult.size - 1)]
            Text("${ddmm(b.fecha)}: ${horas(b.dur)} · 🔥 ${b.kcal}" + (b.pm?.let { " · ♥ $it" } ?: "") +
                (b.subida?.let { " · subida ${it.toInt()} s" } ?: "") + (b.rec?.let { " · recup. ${it.toInt()} s" } ?: ""),
                color = Tinta, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}


// =================== TOTAL: DÍA, SEMANA, MES ===================

/** Una actividad cualquiera (sesión de gym, partido o bici) con lo que importa para sumar. */
private data class Actividad(val tipo: String, val fecha: String, val dur: Long, val kcal: Int, val pm: Int?, val pmax: Int?, val est: Boolean)

private fun actividades(): List<Actividad> {
    val l = ArrayList<Actividad>()
    fun num(d: org.json.JSONObject, k: String) = if (d.isNull(k) || !d.has(k)) null else d.optInt(k)
    Docs.keys("gym-").forEach { k ->
        val d = Docs.get(k) ?: return@forEach
        l += Actividad("gym", d.optString("fecha"), d.optLong("dur"), d.optInt("kcal"), num(d, "pm"), num(d, "pmax"), d.optBoolean("kcalEst"))
    }
    Docs.keys("padel-").forEach { k ->
        val d = Docs.get(k) ?: return@forEach
        val f = d.optString("fecha").ifEmpty { k.removePrefix("padel-").take(10) }
        l += Actividad("padel", f, d.optLong("dur"), d.optInt("kcal"), num(d, "pm"), num(d, "pmax"), d.optBoolean("kcalEst"))
    }
    Docs.keys("bici-").forEach { k ->
        val d = Docs.get(k) ?: return@forEach
        val f = d.optString("fecha").ifEmpty { k.removePrefix("bici-").take(10) }
        l += Actividad("bici", f, d.optLong("dur"), d.optInt("kcal"), num(d, "pm"), num(d, "pmax"), false)
    }
    return l
}

private val TIPOS = listOf(Triple("gym", "Gym", Azul), Triple("padel", "Pádel", Verde), Triple("bici", "Bici", Rosa))

@Composable
private fun StatsTotal(sel: String, onSel: (String) -> Unit) {
    var periodo by rememberSaveable { mutableStateOf("semana") }
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fun cal() = Calendar.getInstance().apply { firstDayOfWeek = Calendar.MONDAY; time = runCatching { f.parse(sel)!! }.getOrDefault(java.util.Date()) }
    // ‹ › mueven el día elegido un día, una semana o un mes (y el calendario lo sigue).
    fun mover(n: Int) {
        val c = cal()
        c.add(when (periodo) { "dia" -> Calendar.DAY_OF_YEAR; "semana" -> Calendar.WEEK_OF_YEAR; else -> Calendar.MONTH }, n)
        onSel(f.format(c.time))
    }
    // Días que entran en el periodo del día elegido.
    val dias: List<String> = remember(periodo, sel) {
        val c = cal()
        when (periodo) {
            "dia" -> listOf(sel)
            "semana" -> {
                c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                (0 until 7).map { i -> f.format(c.time).also { c.add(Calendar.DAY_OF_YEAR, 1) } }
            }
            else -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                val n = c.getActualMaximum(Calendar.DAY_OF_MONTH)
                (0 until n).map { f.format(c.time).also { c.add(Calendar.DAY_OF_YEAR, 1) } }
            }
        }
    }
    val todas = actividades()
    val l = todas.filter { it.fecha in dias }
    val titulo = when (periodo) {
        "dia" -> runCatching { corta.format(f.parse(sel)!!) }.getOrDefault(sel)
        "semana" -> "Semana del ${ddmm(dias.first())} al ${ddmm(dias.last())}"
        else -> runCatching { SimpleDateFormat("MMMM yyyy", Locale("es", "ES")).format(f.parse(dias[0])!!).replaceFirstChar { it.uppercase() } }.getOrDefault(dias[0])
    }
    Caja {
        Text("ACTIVIDAD TOTAL", color = Tinta, fontSize = 22.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("dia" to "DÍA", "semana" to "SEMANA", "mes" to "MES").forEach { (id, t) ->
                val sel = periodo == id
                Box(Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(8.dp)).background(if (sel) Tinta else Tarjeta)
                    .border(1.dp, if (sel) Tinta else Linea, RoundedCornerShape(8.dp)).clickable { periodo = id },
                    contentAlignment = Alignment.Center) {
                    Text(t, color = if (sel) Color.White else Tinta, fontSize = 17.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BotonRedondo("‹", 40.dp) { mover(-1) }
            Text(titulo.uppercase(), color = Tinta, fontSize = 20.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            BotonRedondo("›", 40.dp) { mover(1) }
        }
        val kcal = l.sumOf { it.kcal }
        val dur = l.sumOf { it.dur }
        // Pulso medio ponderado por el tiempo de cada actividad.
        val conPm = l.filter { it.pm != null && it.dur > 0 }
        val pm = if (conPm.isEmpty()) null else (conPm.sumOf { it.pm!!.toLong() * it.dur } / conPm.sumOf { it.dur }).toInt()
        val pmax = l.mapNotNull { it.pmax }.maxOrNull()
        FilaCifras(
            Triple("$kcal", "kcal", Naranja),
            Triple(horas(dur), "de ejercicio", Tinta),
            Triple("${l.size}", if (l.size == 1) "actividad" else "actividades", Tinta),
        )
        FilaCifras(
            Triple(pm?.toString() ?: "—", "pulso medio", colorPulso(pm)),
            Triple(pmax?.toString() ?: "—", "pulso máx", colorPulso(pmax)),
            Triple(if (periodo == "dia") "—" else "${kcal / dias.size}", "kcal/día", Naranja),
        )
        // Desglose por actividad.
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Linea)
        TIPOS.forEach { (id, nombre, c) ->
            val t = l.filter { it.tipo == id }
            if (t.isEmpty()) return@forEach
            val tp = t.filter { it.pm != null && it.dur > 0 }
            val tpm = if (tp.isEmpty()) null else (tp.sumOf { it.pm!!.toLong() * it.dur } / tp.sumOf { it.dur }).toInt()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Text("  $nombre ×${t.size}", color = Tinta, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text((if (t.any { it.est }) "≈" else "") + "🔥 ${t.sumOf { it.kcal }} · ${horas(t.sumOf { it.dur })}" +
                    (tpm?.let { " · ♥ $it" } ?: "") + (t.mapNotNull { it.pmax }.maxOrNull()?.let { " · máx $it" } ?: ""),
                    color = Gris, fontSize = 13.sp)
            }
        }
        if (l.isEmpty()) Text("Sin actividad en este periodo.", color = Gris, fontSize = 14.sp)
        // Barras de kcal por día, apiladas por actividad.
        if (periodo != "dia" && l.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Linea)
            Text("Kcal por día", color = Gris, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val porDia = dias.map { d -> TIPOS.map { (id, _, _) -> l.filter { it.fecha == d && it.tipo == id }.sumOf { it.kcal } } }
            val maxDia = porDia.maxOf { it.sum() }.coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width / dias.size
                porDia.forEachIndexed { i, partes ->
                    var y = size.height
                    partes.forEachIndexed { k, v ->
                        if (v <= 0) return@forEachIndexed
                        val h = size.height * v / maxDia
                        drawRect(TIPOS[k].third, Offset(i * w + w * 0.15f, y - h), androidx.compose.ui.geometry.Size(w * 0.7f, h),
                            alpha = if (dias[i] == sel) 1f else 0.55f)
                        y -= h
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                if (periodo == "semana") listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                    Text(it, color = Gris, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                } else {
                    Text("1", color = Gris, fontSize = 11.sp); Spacer(Modifier.weight(1f)); Text("${dias.size}", color = Gris, fontSize = 11.sp)
                }
            }
            Text("Máximo: $maxDia kcal en un día", color = Gris, fontSize = 11.sp)
        }
        if (l.any { it.est }) Text("≈ incluye kcal estimadas (partidos a mano o gym sin reloj).", color = Gris, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Color del pulso según su zona (los de Polar). */
private fun colorPulso(b: Int?): Color = when (com.bandeja.shared.Zonas.zona(b ?: 0)) {
    1 -> Color(0xFF9AA5A2); 2 -> Color(0xFF1FA2D8); 3 -> Color(0xFF7CAF3A); 4 -> Color(0xFFE8A800); 5 -> Color(0xFFD81B60)
    else -> Tinta
}

/** Lo que se hizo el día elegido en el calendario, de un tipo (gym, padel o bici). */
@Composable
private fun DiaElegido(sel: String, tipo: String) {
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val titulo = runCatching { corta.format(f.parse(sel)!!) }.getOrDefault(sel)
    val l = actividades().filter { it.tipo == tipo && it.fecha == sel }
    Text(titulo.uppercase(), color = Naranja, fontSize = 14.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
    if (l.isEmpty()) Text("Nada este día.", color = Gris, fontSize = 15.sp)
    else {
        val conPm = l.filter { it.pm != null && it.dur > 0 }
        val pm = if (conPm.isEmpty()) null else (conPm.sumOf { it.pm!!.toLong() * it.dur } / conPm.sumOf { it.dur }).toInt()
        val pmax = l.mapNotNull { it.pmax }.maxOrNull()
        FilaCifras(
            Triple((if (l.any { it.est }) "≈" else "") + "${l.sumOf { it.kcal }}", "kcal", Naranja),
            Triple(horas(l.sumOf { it.dur }), if (l.size > 1) "en ${l.size}" else "de ejercicio", Tinta),
            Triple(pm?.toString() ?: "—", "pulso medio", colorPulso(pm)),
        )
        if (pmax != null) Text("Pulso máximo: $pmax", color = colorPulso(pmax), fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Linea)
}

/** Pestaña GYM: qué rutina se hizo el día elegido, con sus números. */
@Composable
private fun StatsGymDia(sel: String) {
    Caja {
        Text("GYM", color = Tinta, fontSize = 22.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        val hechas = Gym.sesiones().filter { (_, fechas) -> sel in fechas }.map { it.key }
        if (hechas.isNotEmpty()) Text(hechas.joinToString(" + ") { Rutina.dia(it)?.tag ?: it }, color = colorDia(hechas.first()),
            fontSize = 20.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        DiaElegido(sel, "gym")
        Periodos(sel, actividades().filter { it.tipo == "gym" }, { it.fecha }) { l ->
            FilaCifras(
                Triple("${l.size}", "sesiones", Tinta),
                Triple(horas(l.sumOf { it.dur }), "entrenadas", Tinta),
                Triple("${l.sumOf { it.kcal }}", "kcal", Naranja),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}
