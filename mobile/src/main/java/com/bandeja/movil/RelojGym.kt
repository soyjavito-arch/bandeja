package com.bandeja.movil

import android.content.Context
import com.bandeja.shared.Bici
import com.bandeja.shared.Sync

/**
 * Cómo se usa el reloj en el gym del móvil (se elige al entrar en Gym):
 * "pulso" = solo manda las pulsaciones en segundo plano (gasta menos), "abrir" = abre también el Gym del reloj,
 * "no" = sin reloj.
 */
object RelojGym {
    fun modo(ctx: Context): String = ctx.getSharedPreferences("gym_movil", Context.MODE_PRIVATE).getString("reloj", "pulso") ?: "pulso"

    fun elegir(ctx: Context, m: String) {
        ctx.getSharedPreferences("gym_movil", Context.MODE_PRIVATE).edit().putString("reloj", m).apply()
        when (m) {
            "abrir" -> Sync.enviar(Bici.CMD, "gym:abrir")
            else -> Sync.enviar(Bici.CMD, "gym:cerrar") // por si estaba abierto de antes
        }
        if (m != "pulso") Sync.enviar(Bici.CMD, "pulso:off")
    }

    /** Al acabar la sesión del móvil: se cierra el Gym del reloj y deja de mandar el pulso. */
    fun cerrar() {
        Sync.enviar(Bici.CMD, "pulso:off")
        Sync.enviar(Bici.CMD, "gym:cerrar")
    }
}
