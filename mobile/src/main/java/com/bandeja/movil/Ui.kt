package com.bandeja.movil

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bandeja.shared.Gifs
import kotlinx.coroutines.delay

// Colores de la web (tema claro)
val Fondo = Color(0xFFEEF1F0)
val Tarjeta = Color(0xFFFFFFFF)
val Tarjeta2 = Color(0xFFE6EBE9)
val Linea = Color(0xFFD5DCDA)
val Tinta = Color(0xFF17201E)
val Gris = Color(0xFF5D6B67)
val Azul = Color(0xFF2B5C9E)
val Verde = Color(0xFF2E7D4F)
val Naranja = Color(0xFFB4531C)
val Morado = Color(0xFF7A3F9E)
val Rosa = Color(0xFFA3346B)
val Oro = Color(0xFFC99A06)
val Rojo = Color(0xFFD63B3B)
val Descanso = Color(0xFFE07B1F)

/** Color de cada entreno (el mismo que en las estadísticas). */
fun colorDia(id: String?): Color = when (id) { "martes" -> Azul; "jueves" -> Naranja; "viernes" -> Morado; else -> Gris }
fun suave(c: Color): Color = c.copy(alpha = 0.14f)

val Barlow = FontFamily(Font(R.font.barlow_semibold, FontWeight.SemiBold), Font(R.font.barlow_bold, FontWeight.Bold))

private fun sobre(c: Color) = if (c.luminance() > 0.5f) Tinta else Color.White

/** Hora actual que se refresca sola (para cronómetros y descansos). */
@Composable
fun ahora(cada: Long = 250): Long {
    var t by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { t = System.currentTimeMillis(); delay(cada) } }
    return t
}

@Composable
fun Gif(clave: String, modifier: Modifier) {
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true } },
        update = { Gifs.poner(it, clave) },
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Color.White),
    )
}

@Composable
fun Caja(modifier: Modifier = Modifier, color: Color = Tarjeta, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color)
            .border(1.dp, Linea, RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(12.dp),
        content = content,
    )
}

@Composable
fun BotonRedondo(texto: String, size: Dp = 44.dp, color: Color = Tarjeta, txt: Color = sobre(color), onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(color).border(1.dp, Linea, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(texto, color = txt, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Pildora(texto: String, color: Color, modifier: Modifier = Modifier, txt: Color = sobre(color), onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).border(1.dp, if (color == Tarjeta) Linea else color, RoundedCornerShape(50))
        .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(texto, color = txt, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** − valor + */
@Composable
fun Paso(valor: String, unidad: String, color: Color = Tinta, onMenos: () -> Unit, onMas: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BotonRedondo("−", 34.dp, onClick = onMenos)
        Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valor, color = color, fontSize = 22.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unidad.isNotEmpty()) Text(unidad, color = Gris, fontSize = 11.sp)
        }
        BotonRedondo("+", 34.dp, onClick = onMas)
    }
}

@Composable
fun Titulo(texto: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(texto.uppercase(), color = Tinta, fontSize = 30.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold)
        if (sub != null) Text(sub, color = Gris, fontSize = 14.sp)
    }
}

/** Cabecera con ⌂ (volver al inicio) o ‹, título y un botón opcional a la derecha. */
@Composable
fun Cabecera(titulo: String, icono: String = "⌂", onAtras: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BotonRedondo(icono, 42.dp, onClick = onAtras)
        Text(titulo.uppercase(), color = Tinta, fontSize = 30.sp, fontFamily = Barlow, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp).weight(1f))
        extra?.invoke()
    }
}
