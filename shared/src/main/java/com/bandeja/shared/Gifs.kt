package com.bandeja.shared

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView

/** GIF animados de los ejercicios (los mismos 27 de la web), guardados en assets/gif. */
object Gifs {
    fun cargar(context: Context, clave: String): Drawable? = runCatching {
        val src = ImageDecoder.createSource(context.assets, "gif/$clave.gif")
        ImageDecoder.decodeDrawable(src)
    }.getOrNull()

    fun existe(context: Context, clave: String): Boolean =
        runCatching { context.assets.open("gif/$clave.gif").close(); true }.getOrDefault(false)

    /** Pone el GIF en un ImageView y lo arranca (o lo deja quieto en la primera imagen si [fijo]). */
    fun poner(view: ImageView, clave: String, fijo: Boolean = false) {
        if (view.tag == clave) return
        view.tag = clave
        val d = cargar(view.context, clave)
        view.setImageDrawable(d)
        if (!fijo) (d as? AnimatedImageDrawable)?.apply { repeatCount = AnimatedImageDrawable.REPEAT_INFINITE; start() }
    }
}
