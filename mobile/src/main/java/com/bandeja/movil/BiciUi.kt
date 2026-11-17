package com.bandeja.movil

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandeja.shared.Bici
import com.bandeja.shared.Docs
import com.bandeja.shared.Fechas
import com.bandeja.shared.Sync
import com.bandeja.shared.Zonas
import org.json.JSONObject

private fun colorZona(z: Int) = when (z) {
    1 -> Gris; 2 -> Azul; 3 -> Verde; 4 -> Descanso; 5 -> Rojo; else -> Gris
}

@Composable
fun BiciTab(v: Int, onInicio: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ver = v
    val now = ahora(1000)
    val vivo = Bici.vivo()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Cabecera("Bici", onAtras = onInicio)
        if (vivo != null && vivo.activa && now - vivo.t < 30_000) EnMarcha(vivo)
        else Preparar()
        Spacer(Modifier.height(18.dp))
        Historial()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun EnMarcha(v: Bici.Vivo) {
    var parar by remember { mutableStateOf(false) }
    val z = Zonas.zona(v.hr)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (v.fuente == "polar") "Polar H10 · pecho" else "Pulso del reloj", color = Gris, fontSize = 15.sp)
        Spacer(Modifier.weight(1f))
        Text(Fechas.hms(v.segundos), color = Tinta, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
    Caja(color = Tarjeta2) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (v.hr > 0) "${v.hr}" else "--", color = colorZona(z), fontSize = 84.sp, fontWeight = FontWeight.ExtraBold)
            Text(" ppm", color = Gris, fontSize = 20.sp, modifier = Modifier.padding(bottom = 18.dp))
        }
        Text(if (z > 0) Zonas.nombre(z) else "", color = colorZona(z), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (v.pausa) Text("EN PAUSA", color = Oro, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Grafica(v.hrs, Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Dato("Media", "${v.media}")
            Dato("Máx", "${v.max}")
            Dato("Kcal", "${v.kcal}")
            v.km?.let { Dato("Km", "%.2f".format(it).replace('.', ',')) }
        }
    }
    v.ritmo?.let { r ->
        Spacer(Modifier.height(10.dp))
        CajaRitmo(r)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pildora(if (v.pausa) "▶ Seguir" else "❚❚ Pausa", Tarjeta2, Modifier.weight(1f)) { Sync.enviar(Bici.CMD, "pausa") }
        Pildora("■ Terminar", Rojo, Modifier.weight(1f)) { parar = true }
    }
    if (parar) {
        AlertDialog(
            onDismissRequest = { parar = false },
            title = { Text("¿Terminar la bici?") },
            text = { Text("Se guarda el entreno.") },
            confirmButton = { TextButton({ Sync.enviar(Bici.CMD, "parar"); parar = false }) { Text("Terminar") } },
            dismissButton = { TextButton({ parar = false }) { Text("Seguir") } },
        )
    }
}

@Composable
private fun Dato(t: String, v: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, color = Tinta, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(t, color = Gris, fontSize = 12.sp)
    }
}

@Composable
private fun Grafica(hrs: List<Int>, modifier: Modifier) {
    val aj = Bici.ajustes()
    Canvas(modifier) {
        val datos = hrs.filter { it > 0 }
        if (datos.size < 2) return@Canvas
        val lo = minOf(datos.min(), if (aj.on) aj.lo else 999) - 5
        val hi = maxOf(datos.max(), if (aj.on) aj.hi else 0) + 5
        fun y(b: Int) = size.height * (1f - (b - lo).toFloat() / (hi - lo).coerceAtLeast(1))
        if (aj.on) {
            listOf(aj.lo to Azul, aj.hi to Rojo).forEach { (b, c) ->
                drawLine(c.copy(alpha = 0.5f), Offset(0f, y(b)), Offset(size.width, y(b)), strokeWidth = 2f)
            }
        }
        val p = Path()
        datos.forEachIndexed { i, b ->
            val x = size.width * i / (datos.size - 1)
            if (i == 0) p.moveTo(x, y(b)) else p.lineTo(x, y(b))
        }
        drawPath(p, Descanso, style = Stroke(width = 5f))
    }
}

/** Recuadro de los cambios de ritmo: en qué cambio vas y el tiempo de cada uno. */
@Composable
private fun CajaRitmo(r: JSONObject) {
    val estado = r.optString("estado")
    val n = r.optInt("n"); val num = r.optInt("num")
    val ms = r.optLong("ms")
    Caja(color = Tarjeta2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cambios de ritmo", color = Tinta, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text("${r.optInt("lo")} → ${r.optInt("hi")} ppm", color = Gris, fontSize = 14.sp)
        }
        val (txt, color) = when (estado) {
            "esperando" -> "Baja a ${r.optInt("lo")} para empezar" to Gris
            "subiendo" -> "Cambio $num de $n · ¡sube!  ${Fechas.mmss(ms / 1000)}" to Naranja
            "recuperando" -> "Recuperando · ${Fechas.mmss(ms / 1000)}" to Azul
            else -> "¡Hecho! $n cambios" to Verde
        }
        Text(txt, color = color, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 6.dp))
        val hechos = r.optJSONArray("hechos")
        for (i in 0 until (hechos?.length() ?: 0)) {
            val h = hechos!!.optJSONArray(i) ?: continue
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text("${i + 1}", color = Gris, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                Text("subida ${Fechas.mmss(h.optLong(0))}", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (h.optLong(1) > 0) "recup. ${Fechas.mmss(h.optLong(1))}" else "", color = Gris, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Preparar() {
    var aj by remember { mutableStateOf(Bici.ajustes()) }
    fun set(n: Bici.Ajustes) { aj = n; Bici.guardar(n) }
    Text("Spinning con el Polar H10 (${Bici.POLAR_ID})", color = Gris, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
    Caja {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (aj.gps) "Exterior · GPS" else "Spinning (interior)", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Activa el GPS solo si sales a la calle", color = Gris, fontSize = 12.sp)
            }
            Switch(checked = aj.gps, onCheckedChange = { set(aj.copy(gps = it)) })
        }
    }
    Spacer(Modifier.height(10.dp))
    Caja {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cambios de ritmo", color = Tinta, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = aj.on, onCheckedChange = { set(aj.copy(on = it)) })
        }
        if (aj.on) {
            Fila("Número de cambios", "${aj.n}", { set(aj.copy(n = (aj.n - 1).coerceAtLeast(1))) }, { set(aj.copy(n = (aj.n + 1).coerceAtMost(30))) })
            Fila("«Start» al bajar a", "${aj.lo}", { set(aj.copy(lo = (aj.lo - 1).coerceAtLeast(60))) }, { set(aj.copy(lo = (aj.lo + 1).coerceAtMost(aj.hi - 5))) })
            Fila("«Break» al llegar a", "${aj.hi}", { set(aj.copy(hi = (aj.hi - 1).coerceAtLeast(aj.lo + 5))) }, { set(aj.copy(hi = (aj.hi + 1).coerceAtMost(210))) })
            Text("El último dice «Finish».", color = Gris, fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(12.dp))
    Pildora("Empezar con el Polar", Naranja, Modifier.fillMaxWidth()) { Sync.enviar(Bici.CMD, "start:polar") }
    Spacer(Modifier.height(8.dp))
    Pildora("Empezar con el pulso del reloj", Tarjeta2, Modifier.fillMaxWidth()) { Sync.enviar(Bici.CMD, "start:reloj") }
    Text("Se graba en el reloj. Si no arranca desde aquí, ábrelo en el reloj: Bici.", color = Gris, fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Fila(t: String, v: String, menos: () -> Unit, mas: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(t, color = Tinta, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Paso(v, "", onMenos = menos, onMas = mas)
    }
}

@Composable
private fun Historial() {
    val claves = Docs.keys("bici-").sortedDescending()
    if (claves.isEmpty()) return
    Text("Entrenos", color = Tinta, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    claves.take(30).forEach { k ->
        val d = Docs.get(k) ?: return@forEach
        val ini = Fechas.desdeIso(d.optString("inicio")) ?: 0L
        Spacer(Modifier.height(6.dp))
        Caja {
            Row {
                Text("${Fechas.corto(ini)} · ${Fechas.hora(ini)}", color = Gris, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(if (d.optString("modo") == "exterior") "EXTERIOR" else "SPINNING", color = Naranja, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
            val partes = buildList {
                add(Fechas.hms(d.optLong("dur")))
                if (!d.isNull("pm")) add("♥ ${d.optInt("pm")}")
                if (!d.isNull("pmax")) add("máx ${d.optInt("pmax")}")
                add("🔥 ${d.optInt("kcal")} kcal")
                if (d.has("km") && !d.isNull("km")) add("${d.optDouble("km")} km")
            }
            Text(partes.joinToString("  ·  "), color = Tinta, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            d.optJSONArray("cambios")?.let { c ->
                if (c.length() > 0) Text("${c.length()} cambios: " + (0 until c.length()).joinToString("  ") { Fechas.mmss(c.optJSONArray(it)?.optLong(0) ?: 0) },
                    color = Gris, fontSize = 13.sp)
            }
        }
    }
}
