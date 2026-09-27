package com.elysium369.meet.communications

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/** Real phone-to-phone transport. Connection acceptance always requires comparing the displayed code. */
@Singleton
class CommunicationNearbyTransport @Inject constructor(@ApplicationContext context: Context) {
    private val client = Nearby.getConnectionsClient(context)
    private val serviceId = "com.elysium369.meet.communications.v1"
    private val endpointName = "Elysium-"+UUID.randomUUID().toString().take(6)
    val status = MutableStateFlow("Conexión cercana desactivada")
    val discovered = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val invitations = MutableStateFlow<List<NearbyInvitation>>(emptyList())
    private val connected = mutableSetOf<String>()
    private var enabled=false
    var receive: ((String,ByteArray)->Unit)? = null

    private val payloadCallback=object:PayloadCallback() {
        override fun onPayloadReceived(endpointId:String,payload:Payload) {
            val bytes=payload.asBytes() ?: return
            if (endpointId !in connected || bytes.size>30_000) return
            receive?.invoke(endpointId,bytes)
        }
        override fun onPayloadTransferUpdate(endpointId:String,update:PayloadTransferUpdate) {
            if (update.status==PayloadTransferUpdate.Status.FAILURE) status.value="Transferencia cercana fallida; mensaje conservado"
        }
    }
    private val lifecycle=object:ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId:String,info:ConnectionInfo) {
            if (!enabled || invitations.value.size>=4) { client.rejectConnection(endpointId); return }
            invitations.value=invitations.value.filterNot { it.id==endpointId }+NearbyInvitation(endpointId,info.endpointName,info.authenticationDigits)
        }
        override fun onConnectionResult(endpointId:String,result:ConnectionResolution) {
            invitations.value=invitations.value.filterNot { it.id==endpointId }
            if (enabled && result.status.isSuccess) { connected.add(endpointId);status.value="Enlace cercano conectado; esperando mensajes autenticados" }
            else { connected.remove(endpointId);status.value="Conexión rechazada o fallida" }
        }
        override fun onDisconnected(endpointId:String) { connected.remove(endpointId);status.value="Enlace cercano desconectado" }
    }
    private val discovery=object:EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId:String,info:DiscoveredEndpointInfo) {
            if (enabled && discovered.value.size<20) discovered.value=discovered.value.filterNot { it.id==endpointId }+NearbyPeer(endpointId,info.endpointName)
        }
        override fun onEndpointLost(endpointId:String) { discovered.value=discovered.value.filterNot { it.id==endpointId } }
    }

    fun start() {
        if(enabled) return
        enabled=true
        status.value="Buscando teléfonos cercanos…"
        try {
            client.startAdvertising(endpointName,serviceId,lifecycle,AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
                .addOnFailureListener { if(enabled) { stop();status.value="No se pudo anunciar el teléfono; revisa permisos y radios" } }
            client.startDiscovery(serviceId,discovery,DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
                .addOnFailureListener { if(enabled) { stop();status.value="No se pudo buscar; revisa permisos y radios" } }
        } catch (_:Exception) { stop();status.value="Dispositivos cercanos no disponibles" }
    }
    fun connect(id:String) {
        if(!enabled || discovered.value.none { it.id==id }) return
        client.requestConnection(endpointName,id,lifecycle).addOnFailureListener { status.value="No se pudo solicitar la conexión" }
    }
    fun answer(id:String,accept:Boolean) {
        if(invitations.value.none { it.id==id }) return
        if(accept && enabled) client.acceptConnection(id,payloadCallback).addOnFailureListener { status.value="No se pudo aceptar la conexión" }
        else client.rejectConnection(id)
        invitations.value=invitations.value.filterNot { it.id==id }
    }
    fun send(bytes:ByteArray,to:String?=null) {
        require(bytes.size<=30_000)
        val endpoints=if(to==null) connected.toList() else connected.filter { it==to }
        if(endpoints.isEmpty()) return
        client.sendPayload(endpoints,Payload.fromBytes(bytes)).addOnFailureListener { status.value="Envío cercano fallido; se conserva para reintentar" }
    }
    fun stop() {
        enabled=false
        client.stopAdvertising();client.stopDiscovery();client.stopAllEndpoints()
        connected.clear();discovered.value=emptyList();invitations.value=emptyList()
        status.value="Conexión cercana desactivada"
    }
}
data class NearbyPeer(val id:String,val name:String)
data class NearbyInvitation(val id:String,val name:String,val code:String)
