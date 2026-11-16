package com.bandeja.padel

import android.app.Application
import com.bandeja.shared.PadelVivo
import com.bandeja.shared.Sync

class BandejaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Sync.init(this)
        // Órdenes del móvil para el marcador: las aplica la pantalla del marcador si está abierta.
        Sync.escuchar(PadelVivo.CMD) { cmd -> Ordenes.padel?.invoke(cmd) }
        Sync.escuchar(com.bandeja.shared.Bici.CMD) { cmd -> EntrenoService.orden(this, cmd) }
    }
}

/** Puente para órdenes que llegan del móvil. */
object Ordenes {
    var padel: ((String) -> Unit)? = null
}
