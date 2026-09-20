package com.elysium369.meet.safety.evidence

import android.content.Context
import android.net.Uri
import com.elysium369.meet.identity.ActivePrincipalKernel
import com.elysium369.meet.safety.crypto.SafetyPayloadCipher
import com.elysium369.meet.safety.data.local.SafetyReportDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
        require(mime in SafetyEvidencePolicy.allowedMimeTypes) { "Formato no admitido. Usa imagen, video, audio o PDF." }
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
}
