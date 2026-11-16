package com.bandeja.movil

import android.app.Application
import com.bandeja.shared.Sync

class MovilApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sync.init(this)
    }
}
