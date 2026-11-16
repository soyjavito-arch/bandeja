package com.bandeja.shared

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Sincronización móvil ↔ reloj con la Wearable Data Layer.
 *
 * - Documentos ([Docs]): cada uno es un DataItem en /doc/<clave>. La Data Layer los guarda
 *   aunque el otro dispositivo no esté cerca y los entrega al reconectar.
 * - Mensajes: órdenes al momento (sumar punto desde el móvil, empezar la bici…).
 *
 * Las dos apps tienen el mismo paquete y la misma firma: es lo que pide Google para hablarse.
 */
object Sync {
    private const val TAG = "BandejaSync"
    private lateinit var data: DataClient
    private lateinit var msg: MessageClient
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handlers = HashMap<String, (ByteArray) -> Unit>()

    private val _conectado = MutableStateFlow(false)
    /** Hay otro dispositivo (móvil o reloj) conectado ahora mismo. */
    val conectado: StateFlow<Boolean> = _conectado

    private val dataListener = DataClient.OnDataChangedListener { events: DataEventBuffer ->
        events.forEach { ev ->
            val path = ev.dataItem.uri.path ?: return@forEach
            if (!path.startsWith("/doc/")) return@forEach
            val key = Uri.decode(path.removePrefix("/doc/"))
            if (ev.type == DataEvent.TYPE_CHANGED) {
                val raw = DataMapItem.fromDataItem(ev.dataItem).dataMap.getString("raw") ?: return@forEach
                Docs.merge(key, raw)
            }
        }
        events.release()
    }

    private val msgListener = MessageClient.OnMessageReceivedListener { ev: MessageEvent ->
        handlers[ev.path]?.invoke(ev.data)
    }

    @Synchronized
    fun init(context: Context) {
        if (::data.isInitialized) return
        appContext = context.applicationContext
        Docs.init(appContext)
        Semilla.cargar(appContext)
        data = Wearable.getDataClient(appContext)
        msg = Wearable.getMessageClient(appContext)
        data.addListener(dataListener)
        msg.addListener(msgListener)
        Docs.onLocalChange = { key, raw -> push(key, raw) }
        scope.launch {
            // Al arrancar: trae lo que haya del otro lado y manda lo nuestro que sea más nuevo.
            runCatching {
                val items = data.dataItems.await()
                val remote = HashMap<String, String>()
                items.forEach { item ->
                    val path = item.uri.path ?: return@forEach
                    if (path.startsWith("/doc/")) {
                        val raw = DataMapItem.fromDataItem(item).dataMap.getString("raw")
                        if (raw != null) remote[Uri.decode(path.removePrefix("/doc/"))] = raw
                    }
                }
                items.release()
                remote.forEach { (k, raw) -> Docs.merge(k, raw) }
                Docs.allRaw().forEach { (k, raw) -> if (remote[k] != raw) push(k, raw) }
            }.onFailure { Log.w(TAG, "sync inicial", it) }
            comprobarConexion()
        }
    }

    fun comprobarConexion() {
        scope.launch {
            _conectado.value = runCatching {
                Wearable.getNodeClient(appContext).connectedNodes.await().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun push(key: String, raw: String) {
        scope.launch {
            runCatching {
                val req = PutDataMapRequest.create("/doc/" + Uri.encode(key)).apply {
                    dataMap.putString("raw", raw)
                }.asPutDataRequest().setUrgent()
                data.putDataItem(req).await()
            }.onFailure { Log.w(TAG, "push $key", it) }
        }
    }

    /** Manda un mensaje a todos los dispositivos conectados. */
    fun enviar(path: String, payload: String = "") {
        scope.launch {
            runCatching {
                val nodes = Wearable.getNodeClient(appContext).connectedNodes.await()
                _conectado.value = nodes.isNotEmpty()
                nodes.forEach { msg.sendMessage(it.id, path, payload.toByteArray()).await() }
            }.onFailure { Log.w(TAG, "enviar $path", it) }
        }
    }

    fun escuchar(path: String, handler: (String) -> Unit) {
        handlers[path] = { bytes -> handler(String(bytes)) }
    }
}
