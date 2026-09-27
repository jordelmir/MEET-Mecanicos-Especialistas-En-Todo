package com.elysium369.meet.communications

import com.elysium369.meet.data.remote.SupabaseModule
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong

enum class CallConnectionState { IDLE, REQUESTING_AUTHORIZATION, RINGING, INCOMING, CONNECTING, ACTIVE, ENDED, FAILED }
sealed interface CallTransportOutcome {
    data object Connected : CallTransportOutcome
    data class Ringing(val callId:String) : CallTransportOutcome
    data object NotConfigured : CallTransportOutcome
    data object AuthenticationRequired : CallTransportOutcome
    data object RejectedInsecureEndpoint : CallTransportOutcome
    data class Failed(val safeCode: String) : CallTransportOutcome
}
interface CallAudioTransport {
    val state: StateFlow<CallConnectionState>
    suspend fun connectAudio(conversationId: String, principalId: String): CallTransportOutcome
    suspend fun connectAudio(conversationId: String, principalId: String, callId:String): CallTransportOutcome = connectAudio(conversationId,principalId)
    suspend fun end()
}
@Serializable
data class CommunicationCallWire(
    val id:String,
    @SerialName("conversation_id") val conversationId:String,
    @SerialName("initiated_by") val initiatedBy:String,
    val state:String,
    @SerialName("livekit_room_name") val room:String,
    @SerialName("media_key") val mediaKey:String?=null,
    @SerialName("answered_by") val answeredBy:String?=null,
)

/** Reuses Viajes' hardware audio engine. Call admission is an authenticated RPC;
 * audio frames use an ephemeral server-issued AES key available only to participants.
 * This is encrypted transport, not a claim of server-blind or forward-secret calls.
 */
@Singleton
class ElysiumCallTransport @Inject constructor(private val audioEngine:RealtimeLiveAudioEngine, private val legacy:ElysiumLiveKitCallTransport):CallAudioTransport {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutex=Mutex()
    private val mutableState=MutableStateFlow(CallConnectionState.IDLE)
    private val legacyMode=MutableStateFlow(false)
    override val state:StateFlow<CallConnectionState> = combine(mutableState,legacy.state,legacyMode) { local,previous,useLegacy -> if(useLegacy) previous else local }.stateIn(scope,SharingStarted.Eagerly,CallConnectionState.IDLE)
    private val mutableIncoming=MutableStateFlow<CommunicationCallWire?>(null)
    val incoming:StateFlow<CommunicationCallWire?> = mutableIncoming.asStateFlow()
    @Volatile private var owner:String?=null
    @Volatile private var accountGeneration=0L
    private var polling:Job?=null
    private var mediaJob:Job?=null
    private var mediaChannel:RealtimeChannel?=null
    @Volatile private var activeCall:CommunicationCallWire?=null
    private var audioStarted=false
    @Volatile private var mediaGeneration=0L

    @Synchronized
    fun watch(principal:String?) {
        if(owner==principal && polling?.isActive==true) return
        val accountChanged = owner != principal
        if (accountChanged && legacyMode.value) {
            legacyMode.value = false
            scope.launch { legacy.end() }
        }
        accountGeneration++
        polling?.cancel(); closeMedia();activeCall=null;mutableIncoming.value=null
        owner=principal;mutableState.value=CallConnectionState.IDLE
        if(principal==null) return
        val accountEpoch=accountGeneration
        polling=scope.launch {
            while(isActive && owner==principal) {
                try {
                    mutex.withLock {
                        if(SupabaseModule.client.auth.currentUserOrNull()?.id!=principal) { closeMedia();mutableState.value=CallConnectionState.ENDED;return@withLock }
                        val current=activeCall
                        if(current!=null) {
                            val receipt=rpc("communication_refresh_call_v1",current.id)
                            if(owner!=principal || accountGeneration!=accountEpoch || SupabaseModule.client.auth.currentUserOrNull()?.id!=principal) return@withLock
                            activeCall=receipt
                            when(receipt.state) {
                                "ACTIVE" -> if(mediaJob?.isActive!=true) startMedia(receipt,principal)
                                "RINGING" -> mutableState.value=CallConnectionState.RINGING
                                else -> { closeMedia();activeCall=null;mutableState.value=CallConnectionState.ENDED }
                            }
                        } else {
                            val ringing=SupabaseModule.client.postgrest["communication_call_sessions"].select {
                                filter { eq("state","RINGING");neq("initiated_by",principal) }
                                order("created_at",io.github.jan.supabase.postgrest.query.Order.DESCENDING);limit(1)
                            }.decodeList<CommunicationCallWire>().firstOrNull()
                            val available=ringing?.let { rpc("communication_refresh_call_v1",it.id) }?.takeIf { it.state=="RINGING" }
                            if(owner!=principal || accountGeneration!=accountEpoch || SupabaseModule.client.auth.currentUserOrNull()?.id!=principal) return@withLock
                            mutableIncoming.value=available
                            if(available!=null) mutableState.value=CallConnectionState.INCOMING
                            else if(mutableState.value==CallConnectionState.INCOMING) mutableState.value=CallConnectionState.IDLE
                        }
                    }
                } catch(c:CancellationException) { throw c }
                catch(_:Exception) { /* RPC failure is not connection proof; media heartbeat enforces teardown. */ }
                delay(2000)
            }
        }
    }
    override suspend fun connectAudio(conversationId:String,principalId:String):CallTransportOutcome {
        if(activeCall!=null || mediaJob?.isActive==true) return CallTransportOutcome.Failed("CALL_BUSY")
        legacyMode.value=true
        return legacy.connectAudio(conversationId,principalId)
    }
    override suspend fun connectAudio(conversationId:String,principalId:String,callId:String):CallTransportOutcome {
        if(legacy.state.value in setOf(CallConnectionState.ACTIVE,CallConnectionState.CONNECTING,CallConnectionState.REQUESTING_AUTHORIZATION)) return CallTransportOutcome.Failed("CALL_BUSY")
        legacyMode.value=false
        if(SupabaseModule.client.auth.currentUserOrNull()?.id!=principalId) return CallTransportOutcome.AuthenticationRequired
        watch(principalId)
        val accountEpoch=accountGeneration
        return mutex.withLock {
            if(activeCall!=null || mediaJob?.isActive==true) return@withLock CallTransportOutcome.Failed("CALL_BUSY")
            mutableState.value=CallConnectionState.REQUESTING_AUTHORIZATION
            try {
                val receipt=SupabaseModule.client.postgrest.rpc("communication_start_call_v1",buildJsonObject {
                    put("p_conversation_id",conversationId);put("p_call_id",callId)
                }).decodeSingle<CommunicationCallWire>()
                check(receipt.id==callId && receipt.conversationId==conversationId && receipt.initiatedBy==principalId && receipt.state=="RINGING")
                synchronized(this) {
                    if(owner!=principalId || accountGeneration!=accountEpoch || SupabaseModule.client.auth.currentUserOrNull()?.id!=principalId) return@withLock CallTransportOutcome.Failed("ACCOUNT_CHANGED")
                    activeCall=receipt;mutableIncoming.value=null;mutableState.value=CallConnectionState.RINGING
                }
                CallTransportOutcome.Ringing(receipt.id)
            } catch(c:CancellationException) { throw c }
            catch(_:Exception) {
                synchronized(this) { if(owner==principalId && accountGeneration==accountEpoch) mutableState.value=CallConnectionState.FAILED }
                CallTransportOutcome.Failed("CALL_AUTHORIZATION_REJECTED")
            }
        }
    }
    suspend fun answer(accept:Boolean):Boolean = mutex.withLock {
        val request=mutableIncoming.value ?: return@withLock false
        val principal=owner ?: return@withLock false
        val accountEpoch=accountGeneration
        if(SupabaseModule.client.auth.currentUserOrNull()?.id!=principal) return@withLock false
        try {
            val receipt=SupabaseModule.client.postgrest.rpc("communication_answer_call_v1",buildJsonObject { put("p_call_id",request.id);put("p_accept",accept) }).decodeSingle<CommunicationCallWire>()
            synchronized(this) {
            if(owner!=principal || accountGeneration!=accountEpoch || SupabaseModule.client.auth.currentUserOrNull()?.id!=principal) return@withLock false
            mutableIncoming.value=null
            if(accept) { check(receipt.state=="ACTIVE" && receipt.answeredBy==principal);activeCall=receipt;startMedia(receipt,principal) }
            else { check(receipt.state=="DECLINED");mutableState.value=CallConnectionState.ENDED }
            }
            true
        } catch(c:CancellationException) { throw c }
        catch(_:Exception) { false }
    }
    private suspend fun rpc(name:String,id:String):CommunicationCallWire = SupabaseModule.client.postgrest.rpc(name,buildJsonObject { put("p_call_id",id) }).decodeSingle()
    @Synchronized
    private fun startMedia(call:CommunicationCallWire,principal:String) {
        check(owner==principal && SupabaseModule.client.auth.currentUserOrNull()?.id==principal)
        val peer=if(call.initiatedBy==principal) requireNotNull(call.answeredBy) else call.initiatedBy
        val crypto=CommunicationCallFrameCipher(requireNotNull(call.mediaKey))
        val channel=SupabaseModule.client.channel(call.room)
        mediaChannel=channel;mutableState.value=CallConnectionState.CONNECTING
        val generation=++mediaGeneration
        mediaJob=scope.launch {
            var startedHere=false
            val chunks=Channel<ByteArray>(10,BufferOverflow.DROP_OLDEST)
            val sequence=AtomicLong(0)
            var lastPeerSequence=0L
            val lastPeerAt=AtomicLong(System.currentTimeMillis())
            try {
                coroutineScope {
                launch {
                    channel.broadcastFlow<CommunicationCallFrame>("media").collect { frame ->
                        if(mediaGeneration!=generation || owner!=principal || SupabaseModule.client.auth.currentUserOrNull()?.id!=principal || frame.call!=call.id || frame.sender!=peer || frame.sequence<=lastPeerSequence) return@collect
                        val bytes=runCatching { crypto.decrypt(frame) }.getOrNull() ?: return@collect
                        lastPeerSequence=frame.sequence;lastPeerAt.set(System.currentTimeMillis())
                        if(audioStarted) mutableState.value=CallConnectionState.ACTIVE
                        if(frame.kind=="AUDIO" && bytes.isNotEmpty()) audioEngine.playAudioChunk(bytes)
                    }
                }
                channel.subscribe()
                synchronized(this@ElysiumCallTransport) {
                    check(mediaGeneration==generation && owner==principal && SupabaseModule.client.auth.currentUserOrNull()?.id==principal)
                    check(audioEngine.start(this) { chunk -> chunks.trySend(chunk.copyOf()) })
                    audioStarted=true;startedHere=true
                }
                launch {
                    val batch=java.io.ByteArrayOutputStream(6400)
                    for(chunk in chunks) {
                        if(mediaGeneration!=generation || owner!=principal || mutableState.value!=CallConnectionState.ACTIVE) { batch.reset();continue }
                        batch.write(chunk)
                        if(batch.size()>=6400) {
                            channel.broadcast("media",crypto.encrypt(call.id,principal,sequence.incrementAndGet(),"AUDIO",batch.toByteArray().copyOfRange(0,6400)))
                            val buffered=batch.toByteArray()
                            batch.reset();batch.write(buffered,6400,buffered.size-6400)
                        }
                    }
                }
                while(isActive && mediaGeneration==generation && owner==principal) {
                    channel.broadcast("media",crypto.encrypt(call.id,principal,sequence.incrementAndGet(),"HELLO",byteArrayOf()))
                    check(System.currentTimeMillis()-lastPeerAt.get()<20_000L)
                    delay(1000)
                }
                }
            } catch(c:CancellationException) { throw c }
            catch(_:Exception) {
                synchronized(this@ElysiumCallTransport) {
                    if(mediaGeneration==generation && owner==principal) { mutableState.value=CallConnectionState.FAILED;activeCall=null }
                }
                runCatching { rpc("communication_end_call_v1",call.id) }
            } finally {
                chunks.close()
                synchronized(this@ElysiumCallTransport) {
                    if(startedHere && mediaGeneration==generation) audioEngine.stop()
                    if(mediaGeneration==generation) audioStarted=false
                }
                withContext(NonCancellable) { runCatching { channel.unsubscribe() } }
            }
        }
    }
    @Synchronized
    private fun closeMedia() {
        mediaGeneration++
        mediaJob?.cancel();mediaJob=null
        if(audioStarted) audioEngine.stop()
        audioStarted=false
        mediaChannel=null
    }
    override suspend fun end() = mutex.withLock {
        if(legacyMode.value) { legacy.end();return@withLock }
        val accountEpoch=accountGeneration
        val principal=owner
        val call=activeCall ?: mutableIncoming.value
        try { if(call!=null) runCatching { rpc("communication_end_call_v1",call.id) } }
        finally { synchronized(this) { if(accountGeneration==accountEpoch && owner==principal) { closeMedia();activeCall=null;mutableIncoming.value=null;mutableState.value=CallConnectionState.ENDED } } }
    }
}
