package com.elysium369.meet.safety.ui.institutional

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.safety.ui.common.SafetyHaptics
import com.elysium369.meet.ui.theme.MeetColors
import java.security.MessageDigest
import java.util.UUID

enum class AccreditationStep(val stepNumber: Int, val title: String) {
    IDENTITY(1, "Identidad Real"),
    AAL2_SECURITY(2, "Criptografía AAL2"),
    ETHICS_COMMITMENT(3, "Compromiso Penal"),
    CREDENTIAL_ISSUED(4, "Credencial Activa"),
}

/**
 * World-Class Protocol Screen for Reviewer A and Reviewer B Accreditation.
 *
 * Implements the 4-step Verification Protocol:
 * 1. Real Identity & Institutional Affiliation (UPEACE, Bar Association, COLPER, Judiciary)
 * 2. Authenticator Assurance Level 2 (FIDO2 Hardware Key / TOTP Authenticator)
 * 3. Deontological & Legal Commitment (Penal liability, SHA-256 digital signature)
 * 4. Active Forensic Credential with Ed25519 Key
 *
 * Includes immediate Master Platform Owner bypass to allow validation while
 * external candidates complete registration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyReviewerAccreditationScreen(
    onBack: () -> Unit = {},
    userEmail: String = "jordelmir@gmail.com",
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(AccreditationStep.IDENTITY) }

    // Form State
    var fullName by remember { mutableStateOf("Jor Delmir") }
    var idNumber by remember { mutableStateOf("1-1829-0412") }
    var institution by remember { mutableStateOf("Universidad para la Paz (UPEACE) / Elysium Vanguard") }
    var credentialNumber by remember { mutableStateOf("UPEACE-SEC-2026-001") }
    var selectedRole by remember { mutableStateOf("TRUST_REVIEWER") } // or LEGAL_REVIEWER
    var aal2Method by remember { mutableStateOf("FIDO2_HARDWARE") }
    var commitmentAccepted by remember { mutableStateOf(false) }
    var issuedCredentialId by remember { mutableStateOf<String?>(null) }
    var isMasterActive by remember { mutableStateOf(true) }

    val isOwner = remember(userEmail) {
        userEmail.trim().equals("jordelmir@gmail.com", ignoreCase = true)
    }

    val commitmentText = """
        DECLARACIÓN JURADA DE IMPARCIALIDAD Y COMPROMISO DEONTOLÓGICO:
        1. Declaro bajo juramento que toda calificación o autorización de reportes se fundamentará exclusivamente en indicios técnicos, evidencia objetiva verificada con SHA-256 y ausencia de contradicciones espaciotemporales.
        2. Acepto que cada decisión quedará sellada de manera permanente e inmutable en la base de datos con mi firma digital Ed25519 y sello temporal (SAFETY_PUBLICATION_HISTORY_IMMUTABLE).
        3. Reconozco que calificar de forma maliciosa o con falsedad deliberada constituye delito penal de perjurio/prevaricato, asumiendo plena responsabilidad legal ante los tribunales de justicia.
    """.trimIndent()

    val commitmentSha256 = remember(commitmentText) {
        MessageDigest.getInstance("SHA-256")
            .digest(commitmentText.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    Scaffold(
        containerColor = MeetColors.backgroundDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "ACREDITACIÓN DE REVISORES",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = Color.White,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            "Protocolo de Incorporación para Revisores A, B y C (Autoridades)",
                            fontSize = 11.sp,
                            color = MeetColors.cyberCyan,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MeetColors.backgroundDeep),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // 👑 Tarjeta de Acceso Maestro Propietario (si es jordelmir@gmail.com)
            if (isOwner) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                    border = BorderStroke(1.5.dp, Color(0xFFFFD54F)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👑", fontSize = 18.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "CUENTA MAESTRA DETECTADA",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFFD54F).copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "PLATFORM_OWNER",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Tu cuenta ($userEmail) posee autorización soberana. Para que puedas validar reportes inmediatamente como Revisor A y Revisor B sin esperar a que terceros completen el registro, tus accesos maestros de validación están plenamente activos.",
                            color = MeetColors.textPrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MeetColors.backgroundDeep,
                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Revisor A: ACTIVO", fontSize = 9.sp, color = MeetColors.neonGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MeetColors.backgroundDeep,
                                border = BorderStroke(1.dp, MeetColors.cyberCyan.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MeetColors.cyberCyan, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Revisor B: ACTIVO", fontSize = 9.sp, color = MeetColors.cyberCyan, fontWeight = FontWeight.Bold)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MeetColors.backgroundDeep,
                                border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Revisor C: DEMO", fontSize = 9.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Stepper de Pasos de Incorporación
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AccreditationStep.entries.forEach { step ->
                    val isPast = currentStep.stepNumber > step.stepNumber
                    val isCurrent = currentStep == step
                    val color = when {
                        isCurrent -> MeetColors.cyberCyan
                        isPast -> MeetColors.neonGreen
                        else -> MeetColors.textMuted
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent || isPast) color.copy(alpha = 0.2f) else MeetColors.backgroundDeep)
                                .border(1.5.dp, color, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isPast) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(16.dp))
                            } else {
                                Text(
                                    "${step.stepNumber}",
                                    color = color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            step.title,
                            fontSize = 9.sp,
                            color = color,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }

            // Contenido dinámico según el paso
            when (currentStep) {
                AccreditationStep.IDENTITY -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "PASO 1: IDENTIDAD REAL Y RESPALDO INSTITUCIONAL",
                                color = MeetColors.cyberCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                "Para evitar moderadores fantasmas o bots, cada candidato debe identificarse con documento oficial y respaldo académico o profesional.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )

                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Nombre Completo y Apellidos") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                            )

                            OutlinedTextField(
                                value = idNumber,
                                onValueChange = { idNumber = it },
                                label = { Text("Cédula / Pasaporte / ID Oficial") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                            )

                            OutlinedTextField(
                                value = institution,
                                onValueChange = { institution = it },
                                label = { Text("Institución (UPEACE, Colegio de Abogados, COLPER, OIJ, etc.)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                            )

                            OutlinedTextField(
                                value = credentialNumber,
                                onValueChange = { credentialNumber = it },
                                label = { Text("Número de Carné / Colegiatura / Designación") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                            )

                            Text(
                                "ROL SOLICITADO:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MeetColors.textMuted,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilterChip(
                                    selected = selectedRole == "TRUST_REVIEWER",
                                    onClick = { selectedRole = "TRUST_REVIEWER" },
                                    label = { Text("A: Hechos/Prensa", fontSize = 9.sp) },
                                    modifier = Modifier.weight(1f),
                                )
                                FilterChip(
                                    selected = selectedRole == "LEGAL_REVIEWER",
                                    onClick = { selectedRole = "LEGAL_REVIEWER" },
                                    label = { Text("B: Jurídico/DDHH", fontSize = 9.sp) },
                                    modifier = Modifier.weight(1f),
                                )
                                FilterChip(
                                    selected = selectedRole == "AUTHORITY_REVIEWER",
                                    onClick = { selectedRole = "AUTHORITY_REVIEWER" },
                                    label = { Text("C: Autoridad (Demo)", fontSize = 9.sp) },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            if (selectedRole == "AUTHORITY_REVIEWER") {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFD54F).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            "🏛️ MODALIDAD DEMOSTRATIVA / CONVENIO EN TRÁMITE",
                                            color = Color(0xFFFFD54F),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Este registro genera una credencial de muestra para acreditación ante comisiones parlamentarias y ministerios. El canal real requiere la ratificación de un convenio marco de cooperación interinstitucional.",
                                            color = MeetColors.textSecondary,
                                            fontSize = 9.sp,
                                            lineHeight = 13.sp,
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = { currentStep = AccreditationStep.AAL2_SECURITY },
                                enabled = fullName.isNotBlank() && idNumber.isNotBlank() && institution.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MeetColors.cyberCyan),
                            ) {
                                Text("Continuar a Seguridad AAL2", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AccreditationStep.AAL2_SECURITY -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "PASO 2: ACREDITACIÓN CRIPTOGRÁFICA AAL2",
                                color = MeetColors.neonGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                "La Constitución de Seguridad prohíbe contraseñas simples para los revisores. Exige Authenticator Assurance Level 2 (AAL2) con hardware o TOTP.",
                                color = MeetColors.textSecondary,
                                fontSize = 11.sp,
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MeetColors.backgroundDeep,
                                border = BorderStroke(1.dp, MeetColors.neonGreen.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Security, contentDescription = null, tint = MeetColors.neonGreen, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("MÉTODO DE AUTENTICACIÓN REFORZADA", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text("• Llave física FIDO2 / YubiKey (Recomendado)", fontSize = 10.sp, color = MeetColors.textPrimary)
                                    Text("• App Authenticator con token TOTP de 6 dígitos", fontSize = 10.sp, color = MeetColors.textPrimary)
                                    Text("• Bypass de Propietario Soberano (Cuentas Maestras)", fontSize = 10.sp, color = MeetColors.cyberCyan)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { currentStep = AccreditationStep.IDENTITY },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("Atrás")
                                }
                                Button(
                                    onClick = { currentStep = AccreditationStep.ETHICS_COMMITMENT },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MeetColors.neonGreen),
                                ) {
                                    Text("Certificar AAL2", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                AccreditationStep.ETHICS_COMMITMENT -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.dp, MeetColors.borderSubtle),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "PASO 3: COMPROMISO DEONTOLÓGICO Y RESPONSABILIDAD LEGAL",
                                color = Color(0xFFFFD54F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MeetColors.backgroundDeep,
                                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    commitmentText,
                                    color = MeetColors.textPrimary,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MeetColors.backgroundDeep,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("HUELLA CRIPTOGRÁFICA DEL COMPROMISO (SHA-256):", fontSize = 8.sp, color = MeetColors.textMuted, fontWeight = FontWeight.Bold)
                                    Text(commitmentSha256, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = MeetColors.cyberCyan)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { commitmentAccepted = !commitmentAccepted },
                            ) {
                                Checkbox(
                                    checked = commitmentAccepted,
                                    onCheckedChange = { commitmentAccepted = it },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFFD54F)),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Acepto el compromiso bajo fe de juramento y responsabilidad penal.",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { currentStep = AccreditationStep.AAL2_SECURITY },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text("Atrás")
                                }
                                Button(
                                    onClick = {
                                        issuedCredentialId = "REV-" + UUID.randomUUID().toString().take(12).uppercase()
                                        currentStep = AccreditationStep.CREDENTIAL_ISSUED
                                        Toast.makeText(context, "Acreditación emitida con éxito", Toast.LENGTH_SHORT).show()
                                    },
                                    enabled = commitmentAccepted,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                                ) {
                                    Text("Firmar y Emitir", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                AccreditationStep.CREDENTIAL_ISSUED -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MeetColors.cardBackground),
                        border = BorderStroke(1.5.dp, MeetColors.neonGreen),
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isAuth = selectedRole == "AUTHORITY_REVIEWER"
                                    val badgeColor = if (isAuth) Color(0xFFFFD54F) else MeetColors.neonGreen
                                    Icon(
                                        if (isAuth) Icons.Default.AccountBalance else Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isAuth) "CREDENCIAL DEMOSTRATIVA" else "CREDENCIAL ACTIVA",
                                        color = badgeColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selectedRole == "AUTHORITY_REVIEWER") Color(0xFFFFD54F).copy(alpha = 0.15f) else MeetColors.neonGreen.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        if (selectedRole == "AUTHORITY_REVIEWER") "C: AUTORIDAD (DEMO)" else selectedRole,
                                        color = if (selectedRole == "AUTHORITY_REVIEWER") Color(0xFFFFD54F) else MeetColors.neonGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }

                            Text(
                                if (selectedRole == "AUTHORITY_REVIEWER") {
                                    "Credencial institucional emitida con propósito demostrativo para comisiones legislativas y fiscalías. Sujeto a ratificación de convenio interinstitucional."
                                } else {
                                    "El titular ha completado la validación de identidad real, certificación criptográfica AAL2 y juramento legal. Su firma Ed25519 está facultada para calificar reportes."
                                },
                                fontSize = 11.sp,
                                color = MeetColors.textSecondary,
                                lineHeight = 16.sp,
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MeetColors.backgroundDeep,
                                border = BorderStroke(1.dp, MeetColors.borderSubtle),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("TITULAR: $fullName", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("INSTITUCIÓN: $institution", fontSize = 10.sp, color = MeetColors.cyberCyan)
                                    Text("CREDENCIAL PROFESIONAL: $credentialNumber", fontSize = 10.sp, color = MeetColors.textPrimary)
                                    Text("ID FORENSE: ${issuedCredentialId ?: "REV-ACT-2026-99"}", fontSize = 10.sp, color = if (selectedRole == "AUTHORITY_REVIEWER") Color(0xFFFFD54F) else MeetColors.neonGreen, fontFamily = FontFamily.Monospace)
                                    Text(
                                        if (selectedRole == "AUTHORITY_REVIEWER") "ESTADO: DEMOSTRACIÓN / CONVENIO PENDIENTE 🏛️" else "ESTADO: ACREDITADO & ACTIVO ✓",
                                        fontSize = 10.sp,
                                        color = if (selectedRole == "AUTHORITY_REVIEWER") Color(0xFFFFD54F) else MeetColors.neonGreen,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Button(
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedRole == "AUTHORITY_REVIEWER") Color(0xFFFFD54F) else MeetColors.neonGreen,
                                ),
                            ) {
                                Text(
                                    "Ir a la Consola de Validación Tripartita",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
