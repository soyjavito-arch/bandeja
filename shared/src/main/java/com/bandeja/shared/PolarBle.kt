package com.bandeja.shared

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

/**
 * Pulsómetro Polar H10 por Bluetooth (perfil estándar de pulso, sin SDK de Polar).
 * Busca el que se llama "Polar H10 E2649629" y avisa con cada latido nuevo (≈1 por segundo).
 * El H10 admite dos conexiones a la vez, así que puede seguir conectado a la app de Polar.
 */
@SuppressLint("MissingPermission")
class PolarBle(context: Context, private val onPulso: (Int) -> Unit, private val onEstado: (String) -> Unit) {
    private val ctx = context.applicationContext
    private val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private var gatt: BluetoothGatt? = null
    private var buscando = false
    private var parado = true
    private val main = Handler(Looper.getMainLooper())

    var conectado = false
        private set

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: return
            if (!name.contains("Polar", true)) return
            // Primero el suyo; si hay otro Polar también vale.
            if (name.contains(Bici.POLAR_ID) || name.contains("H10", true)) {
                pararBusqueda()
                conectar(result.device)
            }
        }
        override fun onScanFailed(errorCode: Int) { onEstado("error") }
    }

    fun empezar() {
        parado = false
        if (adapter == null || !adapter.isEnabled) { onEstado("sin bluetooth"); return }
        // ¿Ya emparejado? Se conecta directamente.
        adapter.bondedDevices?.firstOrNull { (it.name ?: "").contains(Bici.POLAR_ID) }?.let { conectar(it); return }
        buscar()
    }

    private fun buscar() {
        val sc = adapter?.bluetoothLeScanner ?: return
        if (buscando) return
        buscando = true
        onEstado("buscando")
        val filtro = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()
        val ajustes = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { sc.startScan(listOf(filtro), ajustes, scanCb) }.onFailure { buscando = false; onEstado("error") }
    }

    private fun pararBusqueda() {
        if (!buscando) return
        buscando = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCb) }
    }

    private fun conectar(d: BluetoothDevice) {
        onEstado("conectando")
        gatt?.close()
        gatt = if (Build.VERSION.SDK_INT >= 23) d.connectGatt(ctx, false, gattCb, BluetoothDevice.TRANSPORT_LE) else d.connectGatt(ctx, false, gattCb)
    }

    fun parar() {
        parado = true
        pararBusqueda()
        main.removeCallbacksAndMessages(null)
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null
        conectado = false
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                conectado = false
                main.post { onEstado("desconectado") }
                g.close()
                if (gatt == g) gatt = null
                // Reintenta solo mientras la sesión siga en marcha.
                if (!parado) main.postDelayed({ if (!parado) empezar() }, 3000)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT) ?: return
            g.setCharacteristicNotification(ch, true)
            val desc = ch.getDescriptor(CCCD) ?: return
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(desc)
            }
            conectado = true
            main.post { onEstado("conectado") }
        }

        @Deprecated("API < 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            leer(c.value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            leer(value)
        }
    }

    private fun leer(v: ByteArray?) {
        if (v == null || v.size < 2) return
        val flags = v[0].toInt()
        val bpm = if (flags and 0x01 != 0 && v.size >= 3) (v[1].toInt() and 0xFF) or ((v[2].toInt() and 0xFF) shl 8)
        else v[1].toInt() and 0xFF
        main.post { onPulso(bpm) }
    }

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
