package com.bandeja.padel

import com.bandeja.shared.Bici
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Recibe las órdenes del móvil aunque la app del reloj esté cerrada
 * (por ejemplo «pulso:on» para mandar el pulso al móvil durante la bici).
 */
class EscuchaMovil : WearableListenerService() {
    override fun onMessageReceived(ev: MessageEvent) {
        if (ev.path == Bici.CMD) EntrenoService.orden(this, String(ev.data))
    }
}
