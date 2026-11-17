package com.bandeja.movil

import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.Manifest
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import com.bandeja.shared.Bici
import com.bandeja.shared.Docs
import com.bandeja.shared.Fechas
import com.bandeja.shared.Sync
import com.bandeja.shared.Zonas
import org.json.JSONObject

/** Colores de las zonas, como en Polar: 1 gris, 2 azul, 3 verde, 4 amarillo, 5 rosa. */
private fun colorZona(z: Int) = when (z) {
    1 -> Color(0xFF9AA5A2); 2 -> Color(0xFF1FA2D8); 3 -> Color(0xFF7CAF3A); 4 -> Color(0xFFE8A800); 5 -> Color(0xFFD81B60)
    else -> Color(0xFFB5BEBB)
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
    val ctx = LocalContext.current
    // Si la graba el móvil se controla aquí; si la graba el reloj, se le mandan las órdenes.
    val enMovil by BiciService.activa.collectAsState()
    val estado by BiciService.estado.collectAsState()
    fun pausa() = if (enMovil) BiciService.pausa(ctx) else Sync.enviar(Bici.CMD, "pausa")
    fun terminar() = if (enMovil) BiciService.parar(ctx) else Sync.enviar(Bici.CMD, "parar")
    val z = Zonas.zona(v.hr)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(if (v.fuente == "polar") "Polar H10 · pecho" else "Pulso del reloj", color = Gris, fontSize = 15.sp)
            Text(if (enMovil) "Grabando en el móvil" + (if (estado.isNotEmpty()) " · $estado" else "") else "Grabando en el reloj",
                color = Gris, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(Fechas.hms(v.segundos), color = Tinta, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
    }
    Caja(color = Tarjeta2) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (v.hr > 0) "${v.hr}" else "--", color = colorZona(z), fontSize = 84.sp, fontWeight = FontWeight.ExtraBold)
            Text(" ppm", color = Gris, fontSize = 20.sp, modifier = Modifier.padding(bottom = 18.dp))
        }
        Text(Zonas.nombre(z), color = colorZona(z), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        // Leyenda de zonas: la de ahora, rellena.
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { i ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                    .background(if (i == z) colorZona(i) else colorZona(i).copy(alpha = 0.18f)).padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center) {
                    Text("Z$i ${Zonas.desde(i)}", color = if (i == z) Color.White else colorZona(i), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (v.pausa) Text("EN PAUSA", color = Oro, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Grafica(v.hrs, Modifier.fillMaxWidth().height(170.dp))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Dato("Media", "${v.media}")
            Dato("Máx", "${v.max}")
            Dato("Kcal", "${v.kcal}")
            v.km?.let { Dato("Km", "%.2f".format(it).replace('.', ',')) }
        }
    }
    Spacer(Modifier.height(10.dp))
    CajaRitmo(v.ritmo)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pildora(if (v.pausa) "▶ Seguir" else "❚❚ Pausa", Tarjeta2, Modifier.weight(1f)) { pausa() }
        Pildora("■ Terminar", Rojo, Modifier.weight(1f)) { parar = true }
    }
    if (parar) {
        AlertDialog(
            onDismissRequest = { parar = false },
            title = { Text("¿Terminar la bici?") },
            text = { Text("Se guarda el entreno.") },
            confirmButton = { TextButton({ terminar(); parar = false }) { Text("Terminar") } },
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
        // Números de las zonas a la izquierda (como en Polar); la gráfica empieza después.
        val m = 34.dp.toPx()
        val ancho = size.width - m
        val lo = minOf(datos.minOrNull() ?: 999, Zonas.desde(1)) - 4
        val hi = maxOf(datos.maxOrNull() ?: 0, Zonas.desde(6)) + 4
        fun y(b: Int) = size.height * (1f - (b - lo).toFloat() / (hi - lo).coerceAtLeast(1))
        (1..5).forEach { z ->
            val top = y(if (z == 5) hi else Zonas.desde(z + 1))
            val bot = y(if (z == 1) lo else Zonas.desde(z))
            drawRect(colorZona(z).copy(alpha = 0.16f), Offset(m, top), androidx.compose.ui.geometry.Size(ancho, bot - top))
        }
        val p = android.graphics.Paint().apply {
            isAntiAlias = true; textSize = 13.sp.toPx(); color = android.graphics.Color.rgb(90, 102, 99)
            textAlign = android.graphics.Paint.Align.RIGHT; isFakeBoldText = true
        }
        (1..6).forEach { z ->
            val b = Zonas.desde(z)
            drawLine(Linea, Offset(m, y(b)), Offset(size.width, y(b)), strokeWidth = 1.5f)
            drawContext.canvas.nativeCanvas.drawText("$b", m - 6.dp.toPx(), y(b) + p.textSize * 0.35f, p)
        }
        if (aj.on) {
            listOf(aj.lo, aj.hi).forEach { b ->
                drawLine(Tinta.copy(alpha = 0.45f), Offset(m, y(b)), Offset(size.width, y(b)), strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            }
        }
        if (datos.size < 2) return@Canvas
        // Cada tramo de la línea va del color de su zona.
        for (i in 1 until datos.size) {
            val x0 = m + ancho * (i - 1) / (datos.size - 1)
            val x1 = m + ancho * i / (datos.size - 1)
            drawLine(colorZona(Zonas.zona(datos[i])), Offset(x0, y(datos[i - 1])), Offset(x1, y(datos[i])), strokeWidth = 6f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/**
 * Cambios de ritmo en plena bici: se encienden, se ajustan y se ven aquí.
 * El cronómetro arranca al bajar a «Start», se para al llegar a «Break» y vuelve a arrancar en el siguiente «Start».
 */
@Composable
private fun CajaRitmo(r: JSONObject?) {
    var aj by remember { mutableStateOf(Bici.ajustes()) }
    fun set(n: Bici.Ajustes) { aj = n; Bici.guardar(n) }
    val estado = r?.optString("estado") ?: "esperando"
    val num = r?.optInt("num") ?: 0
    // Subiendo: tiempo del esfuerzo. Recuperando: tiempo del descanso, que corre hasta volver a Start.
    val ms = (if (estado == "recuperando") r?.optLong("rec") else r?.optLong("ms")) ?: 0L
    Caja(color = Tarjeta2) {
        // Sin interruptor: el botón Empezar/Pausar es lo que los enciende y los para.
        Text("Cambios de ritmo", color = Tinta, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        run {
            val corriendo = estado == "subiendo" && aj.marcha
            val (txt, color) = if (!aj.marcha) (if (num == 0) "Toca Empezar cuando estés listo" else "En pausa") to Gris else when (estado) {
                "esperando" -> "Baja a ${aj.lo} para empezar" to Gris
                "subiendo" -> "¡Sube! Break a ${aj.hi}" to Naranja
                "recuperando" -> "Descanso · Start a ${aj.lo}" to Azul
                else -> "¡Hecho! ${aj.n} cambios" to Verde
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (num > 0) "CAMBIO $num DE ${aj.n}" else "CAMBIO 1 DE ${aj.n}", color = Gris, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(txt, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(Fechas.mmss(ms / 1000), color = if (corriendo) Naranja else if (estado == "recuperando" && aj.marcha) Azul else Gris, fontSize = 48.sp,
                    fontFamily = Barlow, fontWeight = FontWeight.Bold)
            }
            // Empezar / Pausar: con pausa no cuenta ni avisa; al seguir, el cronómetro sigue donde estaba.
            Pildora(if (aj.marcha) "❚❚ Pausar cambios" else if (num == 0) "▶ Empezar cambios" else "▶ Seguir cambios",
                if (aj.marcha) Tarjeta else Naranja, Modifier.fillMaxWidth().padding(vertical = 4.dp)) { set(if (aj.marcha) aj.copy(marcha = false) else aj.copy(on = true, marcha = true)) }
            Fila("Número de cambios", "${aj.n}", { set(aj.copy(n = (aj.n - 1).coerceAtLeast(1))) }, { set(aj.copy(n = (aj.n + 1).coerceAtMost(30))) })
            Fila("«Start» al bajar a", "${aj.lo}", { set(aj.copy(lo = (aj.lo - 1).coerceAtLeast(60))) }, { set(aj.copy(lo = (aj.lo + 1).coerceAtMost(aj.hi - 5))) })
            Fila("«Break» al llegar a", "${aj.hi}", { set(aj.copy(hi = (aj.hi - 1).coerceAtLeast(aj.lo + 5))) }, { set(aj.copy(hi = (aj.hi + 1).coerceAtMost(210))) })
            val hechos = r?.optJSONArray("hechos")
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
    Text("Los cambios de ritmo se encienden y ajustan durante la bici, en la parte de abajo.", color = Gris, fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp))
    Spacer(Modifier.height(12.dp))
    val ctx = LocalContext.current
    var conPolar by remember { mutableStateOf(true) }
    // Bluetooth (Polar), ubicación (solo con GPS) y avisos; luego empieza aunque se niegue alguno.
    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        BiciService.empezar(ctx, conPolar)
    }
    fun empezar(polar: Boolean) {
        conPolar = polar
        val lista = buildList {
            if (Build.VERSION.SDK_INT >= 31) { add(Manifest.permission.BLUETOOTH_SCAN); add(Manifest.permission.BLUETOOTH_CONNECT) }
            else add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (aj.gps) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.distinct().filter { ctx.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (lista.isEmpty()) BiciService.empezar(ctx, polar) else permisos.launch(lista.toTypedArray())
    }
    Pildora("Empezar con el Polar", Naranja, Modifier.fillMaxWidth()) { empezar(true) }
    Spacer(Modifier.height(8.dp))
    Pildora("Empezar con el pulso del reloj", Tarjeta2, Modifier.fillMaxWidth()) { empezar(false) }
    Text("Se graba en el móvil. Con el Polar no hace falta el reloj; con el pulso del reloj no hace falta abrir la app en el reloj. " +
        "Para ir solo con el reloj, ábrelo allí: Bici.", color = Gris, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
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
