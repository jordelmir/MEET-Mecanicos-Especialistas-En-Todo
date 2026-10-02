package com.elysium369.meet.safety.evidence

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.safety.crypto.SafetyPayloadCipher
import com.elysium369.meet.safety.data.local.SafetyReportDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flatMapLatest
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafetyEvidenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val principal: ActivePrincipalKernel,
    private val dao: SafetyEvidenceDao,
    private val reportDao: SafetyReportDao,
    private val cipher: SafetyPayloadCipher,
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observe(reportId: String) = principal.activePrincipal.flatMapLatest { dao.observe(it.id, reportId) }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeOwner() = principal.activePrincipal.flatMapLatest { dao.observeOwner(it.id) }

    suspend fun stage(reportId: String, uri: Uri): SafetyEvidenceEntity = withContext(Dispatchers.IO) {
        val owner = principal.current()
        check(runCatching { UUID.fromString(owner.id) }.isSuccess) { "Inicia sesión para adjuntar evidencia." }
        UUID.fromString(reportId)
        val mime = context.contentResolver.getType(uri)?.lowercase()
        require(mime in SafetyEvidencePolicy.allowedMimeTypes) { "Formato no admitido. Usa imágenes, audios o documentos/PDFs. Para videos, adjunta el link." }
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "No se pudo abrir el archivo." }.use(SafetyEvidencePolicy::readBounded)
        val id = UUID.randomUUID().toString()
        val directory = File(context.noBackupFilesDir, "safety_evidence/${owner.id}").apply { mkdirs() }
        val target = File(directory, "$id.aead")
        val temp = File(directory, "$id.tmp")
        try {
            val encrypted = cipher.encryptAead(bytes, SafetyEvidencePolicy.associatedData(owner.id, reportId, id)).toWire()
            FileOutputStream(temp).use { it.write(encrypted); it.fd.sync() }
            check(temp.renameTo(target)) { "No se pudo guardar la evidencia." }
            check(principal.current().id == owner.id) { "La sesión cambió. Vuelve a adjuntar el archivo." }
            val entity = SafetyEvidenceEntity(id, reportId, owner.id, target.absolutePath, SafetyEvidencePolicy.sha256(bytes), mime!!, bytes.size.toLong(), System.currentTimeMillis())
            dao.insert(entity)
            entity
        } catch (error: Throwable) {
            temp.delete()
            target.delete()
            throw error
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun removeDraft(id: String) = withContext(Dispatchers.IO) {
        val owner = principal.current().id
        val item = dao.get(id, owner) ?: return@withContext
        check(reportDao.get(item.reportId) == null) { "Un reporte enviado conserva su evidencia." }
        if (dao.removeDraft(id, owner) == 1) File(item.encryptedPath).delete()
    }

    suspend fun getDecryptedBytes(evidenceId: String): ByteArray? = withContext(Dispatchers.IO) {
        val owner = principal.current().id
        val item = dao.get(evidenceId, owner) ?: return@withContext null
        val sourceFile = File(item.encryptedPath)
        if (!sourceFile.exists()) return@withContext null
        try {
            val aad = SafetyEvidencePolicy.associatedData(owner, item.reportId, item.evidenceId)
            val wire = sourceFile.readBytes()
            cipher.decryptAead(com.elysium369.meet.safety.crypto.AeadBlob.fromWire(wire), aad)
        } catch (e: Exception) {
            android.util.Log.e("SafetyEvidence", "Failed to decrypt evidence $evidenceId", e)
            null
        }
    }

    suspend fun prepareFileForViewing(evidenceId: String): Pair<File, String>? = withContext(Dispatchers.IO) {
        val owner = principal.current().id
        val item = dao.get(evidenceId, owner) ?: return@withContext null
        val bytes = getDecryptedBytes(evidenceId) ?: return@withContext null
        val ext = when (item.mimeType) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "image/heic", "image/heif" -> ".heic"
            "video/mp4" -> ".mp4"
            "video/webm" -> ".webm"
            "video/3gpp" -> ".3gp"
            "video/quicktime" -> ".mov"
            "audio/mpeg" -> ".mp3"
            "audio/mp4", "audio/aac" -> ".m4a"
            "audio/ogg" -> ".ogg"
            "audio/wav", "audio/x-wav" -> ".wav"
            "application/pdf" -> ".pdf"
            "text/plain" -> ".txt"
            "text/csv" -> ".csv"
            "application/msword" -> ".doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx"
            "application/vnd.ms-excel" -> ".xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx"
            else -> ".bin"
        }
        val cacheDir = File(context.cacheDir, "safety_evidence").apply { mkdirs() }
        val viewFile = File(cacheDir, "ev_${item.evidenceId.take(8)}$ext")
        viewFile.writeBytes(bytes)
        Pair(viewFile, item.mimeType)
    }

    fun openEvidence(context: Context, evidenceId: String) {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val result = prepareFileForViewing(evidenceId) ?: return@launch
            val (file, mimeType) = result
            withContext(Dispatchers.Main) {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val chooser = Intent.createChooser(intent, "Abrir archivo adjunto").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    android.util.Log.e("SafetyEvidence", "Failed to launch viewer intent", e)
                }
            }
        }
    }
}
