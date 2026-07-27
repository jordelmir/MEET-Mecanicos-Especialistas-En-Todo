package com.elysium369.meet.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerificationCameraPolicyTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing camera permission requests permission before launch`() {
        assertEquals(
            VerificationCameraAction.REQUEST_PERMISSION,
            VerificationCameraPolicy.nextAction(cameraPermissionGranted = false),
        )
        assertEquals(
            VerificationCameraAction.LAUNCH_CAMERA,
            VerificationCameraPolicy.nextAction(cameraPermissionGranted = true),
        )
    }

    @Test
    fun `capture is accepted only when camera reports success and file has bytes`() {
        val emptyFile = temporaryFolder.newFile("empty.jpg")
        val capturedFile = temporaryFolder.newFile("captured.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }

        assertNull(
            VerificationCameraPolicy.completedCapturePath(
                cameraReportedSuccess = false,
                file = capturedFile,
            ),
        )
        assertNull(
            VerificationCameraPolicy.completedCapturePath(
                cameraReportedSuccess = true,
                file = emptyFile,
            ),
        )
        assertEquals(
            capturedFile.absolutePath,
            VerificationCameraPolicy.completedCapturePath(
                cameraReportedSuccess = true,
                file = capturedFile,
            ),
        )
    }

    @Test
    fun `capture files stay inside private verification directory with safe names`() {
        val file = VerificationCameraPolicy.newCaptureFile(
            filesDir = temporaryFolder.root,
            ownerPrefix = "Passenger",
            documentType = "../Cédula Frente",
            timestampEpochMs = 123L,
        )

        assertEquals(
            temporaryFolder.root.resolve("meet_verifications").canonicalFile,
            requireNotNull(file.parentFile).canonicalFile,
        )
        assertEquals("passenger_c_dula_frente_123.jpg", file.name)
    }
}
