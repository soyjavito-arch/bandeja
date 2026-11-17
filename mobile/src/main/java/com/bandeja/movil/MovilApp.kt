package com.bandeja.movil

import android.app.Application
import com.bandeja.shared.Sync

class MovilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sync.init(this)
        // Pulso que manda el reloj mientras el móvil graba la bici.
        Sync.escuchar(BiciService.PULSO_RELOJ) { b -> b.trim().toIntOrNull()?.let { BiciService.pulsoReloj(it) } }
    }
}
