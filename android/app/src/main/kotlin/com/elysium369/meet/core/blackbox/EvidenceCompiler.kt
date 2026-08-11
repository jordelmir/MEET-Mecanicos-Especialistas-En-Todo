package com.elysium369.meet.core.blackbox

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.elysium369.meet.data.local.entities.EvidencePackageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EvidenceCompiler {
    private const val TAG = "EvidenceCompiler"
    private const val KEY_ALIAS = "ELYSIUM_VANGUARD_EVIDENCE_SIGNING_V2"
    private const val VAULT_KEY_VERSION = 2
    private const val VAULT_KEY_ALIAS = "ELYSIUM_VANGUARD_EVIDENCE_VAULT_V$VAULT_KEY_VERSION"

    /**
     * Compiles, hashes, and cryptographically signs a Black Box Evidence Package.
     * Generates a zip archive containing the video, telemetry log, and an official PDF.
     */
    suspend fun compilePackage(
        context: Context,
        vehicleId: String,
        eventType: String,
        gpsLocation: String,
        videoFile: File,
        audioFile: File?,
        telemetryJson: String,
        dtcsList: List<String>
    ): EvidencePackageEntity = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val formattedDate = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
        val outputDir = File(context.filesDir, "blackbox_evidence").apply { mkdirs() }
        
        // 1. Generate PDF Report on Android canvas
        val pdfFile = File(outputDir, "incident_report_$formattedDate.pdf")
        generatePdfReport(pdfFile, eventType, gpsLocation, timestamp, telemetryJson, dtcsList)

        // 2. Generate telemetry JSON file
        val jsonFile = File(outputDir, "telemetry_$formattedDate.json").apply {
            writeText(telemetryJson)
        }

        // 3. Zip files together
        val zipFile = File(outputDir, "evidence_package_$formattedDate.zip.tmp")
        val filesToZip = mutableListOf<File>()
        filesToZip.add(pdfFile)
        filesToZip.add(jsonFile)
        if (videoFile.exists()) filesToZip.add(videoFile)
        if (audioFile != null && audioFile.exists()) filesToZip.add(audioFile)

        try {
            zipFiles(filesToZip, zipFile)
        } catch (error: Throwable) {
            zipFile.delete()
            throw error
        } finally {
            // These two files are compiler-owned plaintext temporaries.
            pdfFile.delete()
            jsonFile.delete()
        }

        // 4. Encrypt the complete package at rest before hashing/signing it.
        val encryptedPackage = File(outputDir, "evidence_package_$formattedDate.evp")
        try {
            encryptIntoVault(
                source = zipFile,
                destination = encryptedPackage,
                aad = "EVP2|$VAULT_KEY_VERSION|${encryptedPackage.name}".toByteArray(Charsets.UTF_8),
            )
        } finally {
            if (zipFile.exists() && !zipFile.delete()) {
                encryptedPackage.delete()
                error("No se pudo eliminar el archivo de evidencia temporal sin cifrar")
            }
        }

        // 5. Hash the exact ciphertext that will be retained/exported.
        val (hash, signature) = try {
            val retainedHash = calculateSha256(encryptedPackage)
            retainedHash to signHash(retainedHash)
        } catch (error: Throwable) {
            encryptedPackage.delete()
            throw error
        }

        Log.i(TAG, "Evidence package encrypted, signed, and hashed: ${encryptedPackage.absolutePath}")

        return@withContext EvidencePackageEntity(
            packageId = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            eventType = eventType,
            timestamp = timestamp,
            gpsLocation = "ENCRYPTED_IN_VAULT",
            videoPath = encryptedPackage.absolutePath,
            audioPath = "",
            pidSnapshot = "ENCRYPTED_IN_VAULT",
            dtcs = "ENCRYPTED_COUNT:${dtcsList.size}",
            hashSha256 = hash,
            signatureVersion = signature,
            createdAt = timestamp
        )
    }

    private fun generatePdfReport(
        outputFile: File,
        eventType: String,
        gps: String,
        timestamp: Long,
        telemetryJson: String,
        dtcs: List<String>
    ) {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }
        
        // Draw Header background
        paint.color = Color.parseColor("#0F172A") // Deep Slate Grey
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        // Draw Gold Accent Line
        paint.color = Color.parseColor("#E2E8F0") // Platinum
        canvas.drawRect(0f, 90f, 595f, 94f, paint)

        // Title
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("ACTA DE EVIDENCIA TÉCNICA VEHICULAR", 40f, 40f, paint)
        
        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#94A3B8")
        canvas.drawText("Elysium Vanguard BLACK BOX AUTOMOTIVE LEGAL TELEMETRY", 40f, 65f, paint)

        // Incident Metadata
        paint.color = Color.BLACK
        paint.textSize = 13f
        paint.isFakeBoldText = true
        canvas.drawText("INFORMACIÓN DEL INCIDENTE", 40f, 140f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#334155")
        canvas.drawText("Tipo de Evento: $eventType", 40f, 170f, paint)
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))
        canvas.drawText("Fecha / Hora: $isoTimestamp (UTC)", 40f, 190f, paint)
        canvas.drawText("Coordenadas GPS: $gps", 40f, 210f, paint)

        // Telemetry Metrics
        paint.textSize = 13f
        paint.isFakeBoldText = true
        paint.color = Color.BLACK
        canvas.drawText("TELEMETRÍA EN EL MOMENTO DEL TRIGGER", 40f, 260f, paint)

        paint.textSize = 11f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#475569")
        
        // Simple mock parse since it's displaying baseline parameters
        canvas.drawText("Códigos DTC Activos: ${if (dtcs.isEmpty()) "Ninguno" else dtcs.joinToString(", ")}", 40f, 290f, paint)

        // Safety Warnings / Legal Notice
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRect(40f, 350f, 555f, 480f, paint)
        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(40f, 350f, 555f, 480f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#991B1B") // Dark Red
        paint.isFakeBoldText = true
        canvas.drawText("DECLARACIÓN DE INTEGRIDAD Y CADENA DE CUSTODIA", 55f, 380f, paint)
        
        paint.color = Color.parseColor("#1E293B")
        paint.isFakeBoldText = false
        canvas.drawText("Este documento y los datos se conservan dentro de un vault EVP cifrado", 55f, 410f, paint)
        canvas.drawText("han sido cifrados y firmados digitalmente en el dispositivo de origen.", 55f, 430f, paint)
        canvas.drawText("Cualquier intento de alteración invalidará el sello de verificación hash SHA-256.", 55f, 450f, paint)

        doc.finishPage(page)
        FileOutputStream(outputFile).use { out ->
            doc.writeTo(out)
        }
        doc.close()
    }

    private fun zipFiles(files: List<File>, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            files.forEach { file ->
                FileInputStream(file).use { fis ->
                    val zipEntry = ZipEntry(file.name)
                    zos.putNextEntry(zipEntry)
                    val buffer = ByteArray(65_536) // 64KB for video performance
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var len: Int
            while (fis.read(buffer).also { len = it } > 0) {
                digest.update(buffer, 0, len)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }

    private fun signHash(hash: String): String {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_SIGN or
                    android.security.keystore.KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(ByteArray(32).also { SecureRandom().nextBytes(it) })
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: error("Android Keystore no entregó una clave privada de evidencia")
        requireHardwareProtection(entry.privateKey as ECPrivateKey)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(entry.privateKey)
        signer.update(hash.toByteArray(Charsets.UTF_8))
        val signature = Base64.getEncoder().encodeToString(signer.sign())
        val certificateSha256 = MessageDigest.getInstance("SHA-256")
            .digest(entry.certificate.encoded)
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
        return "ECDSA_P256_SHA256;key=$KEY_ALIAS;certSha256=$certificateSha256;signature=$signature"
    }

    private fun requireHardwareProtection(privateKey: ECPrivateKey) {
        val factory = java.security.KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
        val info = factory.getKeySpec(privateKey, android.security.keystore.KeyInfo::class.java)
        check(info.isInsideSecureHardware) {
            "Firma cancelada: el dispositivo no probó protección criptográfica por hardware"
        }
    }

    private fun encryptIntoVault(source: File, destination: File, aad: ByteArray) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(VAULT_KEY_ALIAS)) {
            val generator = KeyGenerator.getInstance(
                android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
            generator.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    VAULT_KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generator.generateKey()
        }
        val key = (keyStore.getEntry(VAULT_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: error("Android Keystore no entregó la clave del vault")
        reportVaultKeyProtection(key)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        FileOutputStream(destination).use { output ->
            output.write("EVP2".toByteArray(Charsets.US_ASCII))
            output.write(VAULT_KEY_VERSION)
            output.write(aad.size shr 8)
            output.write(aad.size and 0xff)
            output.write(aad)
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            javax.crypto.CipherOutputStream(output, cipher).use { encrypted ->
                FileInputStream(source).use { input -> input.copyTo(encrypted, 64 * 1024) }
            }
        }
    }

    private fun reportVaultKeyProtection(secretKey: SecretKey) {
        val factory = javax.crypto.SecretKeyFactory.getInstance(secretKey.algorithm, "AndroidKeyStore")
        val info = factory.getKeySpec(secretKey, android.security.keystore.KeyInfo::class.java)
        if (!info.isInsideSecureHardware) {
            Log.w(TAG, "Evidence vault key is device-bound but hardware protection is unavailable")
        }
    }
}
