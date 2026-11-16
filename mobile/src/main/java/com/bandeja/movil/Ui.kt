package com.bandeja.movil

import android.widget.ImageView
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bandeja.shared.Gifs
import kotlinx.coroutines.delay

val Fondo = Color(0xFF0B0B0F)
val Tarjeta = Color(0xFF17171F)
val Tarjeta2 = Color(0xFF22222C)
val Gris = Color(0xFF9A9AA6)
val Azul = Color(0xFF2F80ED)
val Verde = Color(0xFF27AE60)
val Naranja = Color(0xFFF2994A)
val Oro = Color(0xFFF2C94C)
val Rojo = Color(0xFFEB5757)

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
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Color.White),
    )
}

@Composable
fun Caja(modifier: Modifier = Modifier, color: Color = Tarjeta, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(14.dp),
        content = content,
    )
}

@Composable
fun BotonRedondo(texto: String, size: Dp = 44.dp, color: Color = Tarjeta2, txt: Color = Color.White, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(texto, color = txt, fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Pildora(texto: String, color: Color, modifier: Modifier = Modifier, txt: Color = Color.White, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Text(texto, color = txt, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** − valor + */
@Composable
fun Paso(valor: String, unidad: String, color: Color = Color.White, onMenos: () -> Unit, onMas: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BotonRedondo("−", 34.dp, onClick = onMenos)
        Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valor, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (unidad.isNotEmpty()) Text(unidad, color = Gris, fontSize = 11.sp)
        }
        BotonRedondo("+", 34.dp, onClick = onMas)
    }
}

@Composable
fun Titulo(texto: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(texto, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        if (sub != null) Text(sub, color = Gris, fontSize = 14.sp)
    }
}
