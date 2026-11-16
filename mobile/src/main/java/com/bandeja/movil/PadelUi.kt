package com.bandeja.movil

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandeja.shared.Docs
import com.bandeja.shared.Fechas
import com.bandeja.shared.PadelVivo
import com.bandeja.shared.Partidos
import com.bandeja.shared.Sync
import org.json.JSONObject
import java.util.Calendar

@Composable
fun PadelTab(v: Int) {
    @Suppress("UNUSED_VARIABLE") val ver = v
    var editando by rememberSaveable { mutableStateOf<String?>(null) }
    val ed = editando
    if (ed != null) {
        BackHandler { editando = null }
        EditarPartido(ed, onCerrar = { editando = null })
        return
    }
    val now = ahora(1000)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Titulo("Pádel")
        val vivo = Docs.get(PadelVivo.DOC)
        if (vivo != null && vivo.optBoolean("activo") && now - vivo.optLong("t") < 3 * 3600_000L) {
            EnDirecto(vivo, now)
            Spacer(Modifier.height(16.dp))
        } else {
            Caja { Text("No hay partido en marcha. Empiézalo en el reloj y lo verás aquí en directo.", color = Gris, fontSize = 14.sp) }
            Spacer(Modifier.height(16.dp))
        }
        Text("Partidos", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        val todos = Partidos.todos()
        if (todos.isEmpty()) Text("Todavía no hay partidos guardados.", color = Gris, fontSize = 14.sp)
        todos.groupBy { Fechas.dia(it.inicio) }.forEach { (_, lista) ->
            Text(Fechas.corto(lista.first().inicio), color = Gris, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            lista.forEach { p -> FilaPartido(p, onAbrir = { editando = p.key }) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun EnDirecto(o: JSONObject, now: Long) {
    val sets = o.optJSONArray("sets")
    val juegos = o.optJSONArray("juegos")
    val puntos = o.optJSONArray("puntos")
    val saque = o.optString("saque")
    val terminado = o.optBoolean("terminado")
    val desde = o.optLong("desde")
    val ms = o.optLong("acum") + if (desde > 0) now - desde else 0
    Caja(color = Tarjeta2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (terminado) "TERMINADO" else "● EN DIRECTO", color = if (terminado) Oro else Rojo, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            if (o.optBoolean("unSet")) Text("   1 SET", color = Oro, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text(Fechas.hms(ms / 1000), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (!o.isNull("aviso")) Text(o.optString("aviso"), color = Oro, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        listOf(Triple("Nosotros", Azul, 0), Triple("Rivales", Verde, 1)).forEach { (nombre, color, i) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (saque == (if (i == 0) "azul" else "verde") && !terminado) Oro else Color.Transparent))
                Spacer(Modifier.width(8.dp))
                Text(nombre, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                for (k in 0 until (sets?.length() ?: 0)) {
                    Text("${sets!!.optJSONArray(k)?.optInt(i)}", color = Gris, fontSize = 18.sp, modifier = Modifier.width(26.dp))
                }
                if (!terminado) {
                    Text("${juegos?.optInt(i) ?: 0}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                    Text(puntos?.optString(i) ?: "0", color = color, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.width(52.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("♥ ${o.optInt("hr")}   ·   🔥 ${o.optInt("kcal")} kcal", color = Gris, fontSize = 14.sp)
        if (!terminado) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pildora("+ Nosotros", Azul, Modifier.weight(1f)) { Sync.enviar(PadelVivo.CMD, "A") }
                Pildora("+ Rivales", Verde, Modifier.weight(1f)) { Sync.enviar(PadelVivo.CMD, "B") }
            }
            Spacer(Modifier.height(8.dp))
            Pildora("↶ Deshacer último punto", Tarjeta, Modifier.fillMaxWidth()) { Sync.enviar(PadelVivo.CMD, "U") }
            Text("Los puntos se apuntan en el reloj (con el marcador abierto).", color = Gris, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilaPartido(p: Partidos.Resumen, onAbrir: () -> Unit) {
    var borrar by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(confirmValueChange = {
        if (it != SwipeToDismissBoxValue.Settled) borrar = true
        false
    })
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Rojo).padding(end = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Text("Borrar", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Caja(onClick = onAbrir) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fechas.hora(p.inicio), color = Gris, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Etiqueta(if (p.unSet) "1 SET" else "3 SETS", if (p.unSet) Oro else Azul)
                if (!p.terminado) { Spacer(Modifier.width(6.dp)); Etiqueta("sin terminar", Gris) }
                Spacer(Modifier.weight(1f))
                val res = when (p.ganador) { "azul" -> "Ganado"; "verde" -> "Perdido"; else -> "" }
                Text(res, color = if (p.ganador == "azul") Verde else Rojo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(p.sets.joinToString("   ") { "${it.first}-${it.second}" }.ifEmpty { "—" },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            val extra = buildList {
                if (p.dur > 0) add(Fechas.hms(p.dur))
                p.pm?.let { add("♥ $it") }
                p.kcal?.let { add("🔥 $it kcal") }
            }.joinToString("  ·  ")
            if (extra.isNotEmpty()) Text(extra, color = Gris, fontSize = 13.sp)
            if (p.nota.isNotEmpty()) Text(p.nota, color = Color(0xFFCFCFD8), fontSize = 13.sp)
        }
    }
    if (borrar) ConfirmarBorrar(onSi = { Docs.delete(p.key); borrar = false }, onNo = { borrar = false })
}

@Composable
private fun Etiqueta(t: String, c: Color) {
    Text(t, color = c, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(c.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
private fun ConfirmarBorrar(onSi: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text("¿Borrar el partido?") },
        text = { Text("Se borra en el móvil y en el reloj.") },
        confirmButton = { TextButton(onSi) { Text("Borrar", color = Rojo) } },
        dismissButton = { TextButton(onNo) { Text("Cancelar") } },
    )
}

@Composable
private fun EditarPartido(key: String, onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val p = remember(key) { Partidos.leer(key) }
    if (p == null) { androidx.compose.runtime.LaunchedEffect(Unit) { onCerrar() }; return }
    val sets = remember(key) { mutableStateListOf<Pair<Int, Int>>().apply { addAll(p.sets.map { it.first to it.second }) } }
    var nota by rememberSaveable(key) { mutableStateOf(p.nota) }
    var unSet by rememberSaveable(key) { mutableStateOf(p.unSet) }
    var fecha by remember(key) { mutableLongStateOf(p.inicio) }
    var borrar by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotonRedondo("‹", 44.dp, onClick = onCerrar)
            Spacer(Modifier.width(12.dp))
            Titulo("Editar partido")
        }
        Caja {
            Text("Fecha y hora", color = Gris, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Pildora(Fechas.corto(fecha), Tarjeta2) {
                    val c = Calendar.getInstance().apply { timeInMillis = fecha }
                    DatePickerDialog(ctx, { _, y, m, d ->
                        fecha = Calendar.getInstance().apply { timeInMillis = fecha; set(y, m, d) }.timeInMillis
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                }
                Pildora(Fechas.hora(fecha), Tarjeta2) {
                    val c = Calendar.getInstance().apply { timeInMillis = fecha }
                    TimePickerDialog(ctx, { _, h, min ->
                        fecha = Calendar.getInstance().apply { timeInMillis = fecha; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min) }.timeInMillis
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Caja {
            Text("Tipo de partido", color = Gris, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Pildora("1 set", if (unSet) Oro else Tarjeta2, txt = if (unSet) Color.Black else Color.White) { unSet = true }
                Pildora("Al mejor de 3", if (!unSet) Azul else Tarjeta2) { unSet = false }
            }
        }
        Spacer(Modifier.height(10.dp))
        Caja {
            Text("Sets (nosotros - rivales)", color = Gris, fontSize = 13.sp)
            sets.forEachIndexed { i, (a, b) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Set ${i + 1}", color = Gris, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                    Paso("$a", "", Azul, onMenos = { sets[i] = (a - 1).coerceAtLeast(0) to b }, onMas = { sets[i] = (a + 1).coerceAtMost(20) to b })
                    Spacer(Modifier.weight(1f))
                    Paso("$b", "", Verde, onMenos = { sets[i] = a to (b - 1).coerceAtLeast(0) }, onMas = { sets[i] = a to (b + 1).coerceAtMost(20) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (sets.size < 3) Pildora("+ Añadir set", Tarjeta2) { sets.add(0 to 0) }
                if (sets.isNotEmpty()) Pildora("Quitar el último", Tarjeta2) { sets.removeAt(sets.size - 1) }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = nota, onValueChange = { nota = it }, label = { Text("Nota") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Pildora("Guardar", Verde, Modifier.fillMaxWidth()) {
            Partidos.editar(key, sets.toList(), nota, unSet, if (fecha != p.inicio) fecha else null)
            onCerrar()
        }
        Spacer(Modifier.height(10.dp))
        Pildora("Borrar partido", Rojo, Modifier.fillMaxWidth()) { borrar = true }
        Spacer(Modifier.height(20.dp))
    }
    if (borrar) ConfirmarBorrar(onSi = { Docs.delete(key); borrar = false; onCerrar() }, onNo = { borrar = false })
}
