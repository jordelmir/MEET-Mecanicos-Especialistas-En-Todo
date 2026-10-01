package com.elysium369.meet.core.mesh

import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Byte contract fixture only: never evidence of reviewed E2EE or functioning radios. */
class MeshWireParityTest {
    @Test fun meshWireV1MatchesCanonicalFixture() {
        val fixture = listOf("../../tests/parity/fixtures/mesh-wire-v1.json", "tests/parity/fixtures/mesh-wire-v1.json", "../tests/parity/fixtures/mesh-wire-v1.json").map(::File).firstOrNull { it.isFile } ?: error("Mesh wire fixture missing")
        val f = Json.parseToJsonElement(fixture.readText()).jsonObject
        fun str(key: String) = f.getValue(key).jsonPrimitive.content
        fun bytes(key: String) = str(key).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val envelope = MeshEnvelope(str("messageId"), str("originKeyId"), str("recipientKeyId"), str("createdAt").toLong(), str("expiresAt").toLong(), str("maxHops").toInt(), str("priority").toInt(), bytes("ciphertextHex"), bytes("authenticationHex"), str("attachmentBytes").toInt(), str("attachmentDigest"), str("hops").toInt())
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        val wire = MeshWire.encode(envelope)
        val computed = buildJsonObject {
            put("aadHex", hex(envelope.associatedData())); put("wireHex", hex(wire))
            put("packetHex", hex(MeshPacketWire.encode(MeshPacket.Envelope(envelope)))); put("sha256", digest(wire))
        }
        assertEquals(str("expectedAadHex"), computed.getValue("aadHex").jsonPrimitive.content)
        assertEquals(str("expectedWireHex"), computed.getValue("wireHex").jsonPrimitive.content)
        assertEquals(str("expectedPacketHex"), computed.getValue("packetHex").jsonPrimitive.content)
        assertEquals(str("expectedSha256"), computed.getValue("sha256").jsonPrimitive.content)
        val output = if (File("build.gradle.kts").isFile && File("src/main").isDirectory) File("build/reports/parity/mesh-wire-v1.json") else File("android/app/build/reports/parity/mesh-wire-v1.json")
        output.parentFile?.mkdirs(); output.writeText(computed.toString())
    }
}
