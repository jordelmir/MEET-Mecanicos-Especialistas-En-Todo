package com.elysium369.meet.communications

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable

@Serializable
internal data class CommunicationCallFrame(val call: String, val sender: String, val sequence: Long, val kind: String, val nonce: String, val ciphertext: String)

internal class CommunicationCallFrameCipher(key: String) {
    companion object { const val MAX_AUDIO_BYTES = 12_800 }
    private val secret=SecretKeySpec(Base64.getDecoder().decode(key).also { require(it.size==32) },"AES")
    private fun aad(call:String,sender:String,sequence:Long,kind:String)="elysium-call-v1|$call|$sender|$sequence|$kind".toByteArray()
    fun encrypt(call:String,sender:String,sequence:Long,kind:String,bytes:ByteArray):CommunicationCallFrame {
        require(kind in setOf("HELLO","AUDIO") && sequence>0 && bytes.size<=MAX_AUDIO_BYTES)
        val nonce=ByteArray(12).also(SecureRandom()::nextBytes)
        val data=Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.ENCRYPT_MODE,secret,GCMParameterSpec(128,nonce));updateAAD(aad(call,sender,sequence,kind));doFinal(bytes) }
        return CommunicationCallFrame(call,sender,sequence,kind,Base64.getEncoder().encodeToString(nonce),Base64.getEncoder().encodeToString(data))
    }
    fun decrypt(frame:CommunicationCallFrame):ByteArray {
        require(frame.sequence>0 && frame.kind in setOf("HELLO","AUDIO") && frame.ciphertext.length<=17_200)
        val nonce=Base64.getDecoder().decode(frame.nonce).also { require(it.size==12) }
        return Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE,secret,GCMParameterSpec(128,nonce));updateAAD(aad(frame.call,frame.sender,frame.sequence,frame.kind));doFinal(Base64.getDecoder().decode(frame.ciphertext)) }.also { require(it.size<=MAX_AUDIO_BYTES) }
    }
}
