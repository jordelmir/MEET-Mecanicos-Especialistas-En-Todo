package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.elysium369.meet.ui.theme.MeetColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.BuildConfig
import com.elysium369.meet.data.remote.SupabaseModule
import com.elysium369.meet.observability.AuthObservability
import com.elysium369.meet.observability.AuthOperation
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTextButton
import com.elysium369.meet.ui.components.ElysiumSectionIcon
import com.elysium369.meet.ui.components.HolographicBackgroundShared
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    initialMode: AuthFlowMode = AuthFlowMode.LOGIN,
    recoverySessionReady: Boolean = false,
    onRecoveryComplete: () -> Unit = {},
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val isConfigured = BuildConfig.SUPABASE_URL.isNotBlank() &&
        BuildConfig.SUPABASE_KEY.isNotBlank()

    fun submit() {
        if (loading) return
        val operationMode = mode
        val attempt = AuthObservability.start(AuthOperation.valueOf(operationMode.name))
        val normalizedEmail = AuthFormPolicy.normalizeEmail(email)
        feedback = when {
            !isConfigured ->
                "Servicio de cuenta no configurado. La autenticación es necesaria para entrar a MEET."
            operationMode != AuthFlowMode.PASSWORD_UPDATE -> AuthFormPolicy.validateEmail(normalizedEmail)
            !recoverySessionReady ->
                "El enlace no es válido o expiró. Solicita uno nuevo."
            else -> null
        }
        if (feedback == null && operationMode in setOf(AuthFlowMode.SIGN_UP, AuthFlowMode.PASSWORD_UPDATE)) {
            feedback = AuthFormPolicy.validatePassword(password)
                ?: AuthFormPolicy.validateConfirmation(password, passwordConfirmation)
        }
        if (feedback == null && operationMode == AuthFlowMode.LOGIN && password.isBlank()) {
            feedback = "Escribe tu contraseña."
        }
        if (feedback != null) {
            AuthObservability.failed(attempt, "CLIENT_VALIDATION_REJECTED")
            return
        }

        loading = true
        scope.launch {
            var outcomeRecorded = false
            try {
                when (operationMode) {
                    AuthFlowMode.LOGIN -> {
                        SupabaseModule.client.auth.signInWith(Email) {
                            this.email = normalizedEmail
                            this.password = password
                        }
                        if (SupabaseModule.client.auth.currentUserOrNull() != null) {
                            AuthObservability.succeeded(attempt, "AUTHENTICATED")
                            outcomeRecorded = true
                            onAuthSuccess()
                        } else {
                            AuthObservability.failed(attempt, "SESSION_NOT_ESTABLISHED")
                            outcomeRecorded = true
                            feedback = "No se pudo establecer una sesión autenticada."
                        }
                    }
                    AuthFlowMode.SIGN_UP -> {
                        SupabaseModule.client.auth.signUpWith(Email) {
                            this.email = normalizedEmail
                            this.password = password
                        }
                        if (SupabaseModule.client.auth.currentUserOrNull() != null) {
                            AuthObservability.succeeded(attempt, "AUTHENTICATED")
                            outcomeRecorded = true
                            onAuthSuccess()
                        } else {
                            AuthObservability.succeeded(attempt, "CONFIRMATION_PENDING")
                            outcomeRecorded = true
                            feedback = "Cuenta creada. Confirma tu correo y luego inicia sesión."
                            mode = AuthFlowMode.LOGIN
                            password = ""
                            passwordConfirmation = ""
                        }
                    }
                    AuthFlowMode.RECOVERY_REQUEST -> {
                        SupabaseModule.client.auth.resetPasswordForEmail(
                            email = normalizedEmail,
                            redirectUrl = SupabaseModule.PASSWORD_RECOVERY_REDIRECT,
                        )
                        AuthObservability.succeeded(attempt, "RECOVERY_ACCEPTED")
                        outcomeRecorded = true
                        feedback = AuthFailureTranslator.recoveryRequestConfirmation()
                    }
                    AuthFlowMode.PASSWORD_UPDATE -> {
                        SupabaseModule.client.auth.modifyUser {
                            this.password = password
                        }
                        AuthObservability.succeeded(attempt, "PASSWORD_UPDATED")
                        outcomeRecorded = true
                        feedback = "Contraseña actualizada de forma segura."
                        password = ""
                        passwordConfirmation = ""
                        onRecoveryComplete()
                        onAuthSuccess()
                    }
                }
            } catch (error: Exception) {
                if (outcomeRecorded) {
                    feedback = "La cuenta respondió correctamente, pero la sincronización local no terminó. Reabre MEET."
                } else {
                    val failure = AuthFailureTranslator.classify(error.message)
                    AuthObservability.failed(attempt, failure.name)
                    feedback = AuthFailureTranslator.userMessage(operationMode, error.message)
                }
            } finally {
                loading = false
            }
        }
    }

    val title = when (mode) {
        AuthFlowMode.LOGIN -> "Cuenta MEET"
        AuthFlowMode.SIGN_UP -> "Crear cuenta MEET"
        AuthFlowMode.RECOVERY_REQUEST -> "Recuperar acceso"
        AuthFlowMode.PASSWORD_UPDATE -> "Nueva contraseña"
    }
    val description = when (mode) {
        AuthFlowMode.LOGIN ->
            "Inicia sesión para proteger tu identidad, vehículos, evidencia y servicios."
        AuthFlowMode.SIGN_UP ->
            "Toda persona inicia como usuario normal. Después puede solicitar capacidades profesionales, siempre sujetas a verificación."
        AuthFlowMode.RECOVERY_REQUEST ->
            "Te enviaremos un enlace de un solo uso. Por seguridad, MEET no revela si el correo está registrado."
        AuthFlowMode.PASSWORD_UPDATE ->
            if (recoverySessionReady) {
                "El enlace fue validado. Define una contraseña nueva para recuperar tu cuenta."
            } else {
                "Validando el enlace de recuperación…"
            }
    }
    val actionText = when {
        loading -> "PROCESANDO..."
        mode == AuthFlowMode.LOGIN -> "INICIAR SESIÓN"
        mode == AuthFlowMode.SIGN_UP -> "REGISTRARSE"
        mode == AuthFlowMode.RECOVERY_REQUEST -> "ENVIAR ENLACE SEGURO"
        else -> "ACTUALIZAR CONTRASEÑA"
    }

    fun switchTo(target: AuthFlowMode) {
        mode = target
        password = ""
        passwordConfirmation = ""
        feedback = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MeetColors.backgroundDeep)
    ) {
        HolographicBackgroundShared()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EliteCard(
                glowColor = if (mode == AuthFlowMode.LOGIN) MeetColors.neonGreen else MeetColors.cyberCyan,
                borderColor = MeetColors.neonGreen.copy(alpha = 0.28f),
                backgroundColor = MeetColors.cardBackground,
                shape = RoundedCornerShape(20.dp),
                enableHolo3D = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MeetColors.neonGreen.copy(alpha = 0.12f))
                            .border(1.dp, MeetColors.neonGreen.copy(alpha = 0.32f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        ElysiumSectionIcon(
                            key = "scanner",
                            contentDescription = "Elysium",
                            tint = MeetColors.neonGreen,
                            size = 38.dp,
                            fallbackGlyph = "EV"
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        "ELYSIUM",
                        style = MaterialTheme.typography.displayMedium,
                        color = MeetColors.neonGreen,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Vanguard OBD2 Scanner",
                        color = MeetColors.cyberCyan.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 18.dp)
                    )

                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        description,
                        color = MeetColors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                    )

                    if (mode != AuthFlowMode.PASSWORD_UPDATE) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email", color = MeetColors.textSecondary) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = authFieldColors()
                        )
                    }

                    if (mode in setOf(AuthFlowMode.LOGIN, AuthFlowMode.SIGN_UP, AuthFlowMode.PASSWORD_UPDATE)) {
                        if (mode != AuthFlowMode.PASSWORD_UPDATE) {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = {
                                Text(
                                    if (mode == AuthFlowMode.PASSWORD_UPDATE) "Nueva contraseña" else "Contraseña",
                                    color = MeetColors.textSecondary,
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = authFieldColors()
                        )
                    }

                    if (mode in setOf(AuthFlowMode.SIGN_UP, AuthFlowMode.PASSWORD_UPDATE)) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = passwordConfirmation,
                            onValueChange = { passwordConfirmation = it },
                            label = { Text("Confirmar contraseña", color = MeetColors.textSecondary) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = authFieldColors()
                        )
                        Text(
                            "Mínimo 12 caracteres: mayúscula, minúscula, número y símbolo.",
                            color = MeetColors.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    EliteButton(
                        onClick = ::submit,
                        modifier = Modifier.fillMaxWidth(),
                        text = actionText,
                        color = if (mode == AuthFlowMode.LOGIN) MeetColors.neonGreen else MeetColors.cyberCyan,
                        isEnabled = !loading && (mode != AuthFlowMode.PASSWORD_UPDATE || recoverySessionReady),
                    )

                    feedback?.let { message ->
                        Text(
                            text = message,
                            color = MeetColors.warning,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }

                    when (mode) {
                        AuthFlowMode.LOGIN -> {
                            EliteTextButton(
                                onClick = { switchTo(AuthFlowMode.RECOVERY_REQUEST) },
                                text = "¿Olvidaste tu contraseña?",
                                color = MeetColors.cyberCyan,
                            )
                            EliteTextButton(
                                onClick = { switchTo(AuthFlowMode.SIGN_UP) },
                                text = "¿No tienes cuenta? Regístrate",
                                color = MeetColors.textSecondary,
                            )
                        }
                        AuthFlowMode.SIGN_UP,
                        AuthFlowMode.RECOVERY_REQUEST -> EliteTextButton(
                            onClick = { switchTo(AuthFlowMode.LOGIN) },
                            text = "Volver a iniciar sesión",
                            color = MeetColors.textSecondary,
                        )
                        AuthFlowMode.PASSWORD_UPDATE -> if (!recoverySessionReady) {
                            EliteTextButton(
                                onClick = { switchTo(AuthFlowMode.RECOVERY_REQUEST) },
                                text = "Solicitar un enlace nuevo",
                                color = MeetColors.cyberCyan,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = MeetColors.neonGreen,
    unfocusedBorderColor = MeetColors.borderBlue,
    focusedContainerColor = MeetColors.backgroundDeep,
    unfocusedContainerColor = MeetColors.backgroundDeep,
)
