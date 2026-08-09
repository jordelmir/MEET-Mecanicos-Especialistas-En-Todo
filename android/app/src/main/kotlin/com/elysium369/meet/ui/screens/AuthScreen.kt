package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteOutlinedButton
import com.elysium369.meet.ui.components.EliteTextButton
import com.elysium369.meet.ui.components.ElysiumSectionIcon
import com.elysium369.meet.ui.components.HolographicBackgroundShared
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit, onOfflineMode: () -> Unit) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun authenticate() {
        if (loading) return
        val normalizedEmail = email.trim().lowercase()
        feedback = when {
            BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank() ->
                "Servicio de cuenta no configurado. Usa Entrar sin cuenta."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches() ->
                "Escribe un correo electrónico válido."
            password.length < 8 ->
                "La contraseña debe tener al menos 8 caracteres."
            else -> null
        }
        if (feedback != null) return

        loading = true
        scope.launch {
            try {
                if (isLogin) {
                    SupabaseModule.client.auth.signInWith(Email) {
                        this.email = normalizedEmail
                        this.password = password
                    }
                } else {
                    SupabaseModule.client.auth.signUpWith(Email) {
                        this.email = normalizedEmail
                        this.password = password
                    }
                }

                if (SupabaseModule.client.auth.currentUserOrNull() != null) {
                    onAuthSuccess()
                } else if (isLogin) {
                    feedback = "No se pudo establecer una sesión autenticada."
                } else {
                    feedback = "Cuenta creada. Confirma tu correo y luego inicia sesión."
                    isLogin = true
                }
            } catch (error: Exception) {
                feedback = if (isLogin) {
                    "No se pudo iniciar sesión. Verifica tus credenciales y conexión."
                } else {
                    "No se pudo crear la cuenta. Revisa los datos o intenta más tarde."
                }
            } finally {
                loading = false
            }
        }
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            EliteCard(
                glowColor = if (isLogin) MeetColors.neonGreen else MeetColors.cyberCyan,
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
                        if (isLogin) "Cuenta opcional" else "Crear cuenta opcional",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Puedes entrar offline; los perfiles de proveedor se activan dentro de la APK.",
                        color = MeetColors.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email", color = MeetColors.textSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = MeetColors.borderBlue,
                            focusedContainerColor = MeetColors.backgroundDeep,
                            unfocusedContainerColor = MeetColors.backgroundDeep
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña", color = MeetColors.textSecondary) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MeetColors.neonGreen,
                            unfocusedBorderColor = MeetColors.borderBlue,
                            focusedContainerColor = MeetColors.backgroundDeep,
                            unfocusedContainerColor = MeetColors.backgroundDeep
                        )
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    EliteButton(
                        onClick = ::authenticate,
                        modifier = Modifier.fillMaxWidth(),
                        text = if (loading) "ENTRANDO..." else if (isLogin) "INICIAR SESIÓN" else "REGISTRARSE",
                        color = if (isLogin) MeetColors.neonGreen else MeetColors.cyberCyan
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

                    EliteTextButton(
                        onClick = {
                            isLogin = !isLogin
                            feedback = null
                        },
                        text = if (isLogin) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Inicia sesión",
                        color = MeetColors.textSecondary
                    )

                    Divider(color = MeetColors.neonGreen.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))

                    EliteOutlinedButton(
                        onClick = onOfflineMode,
                        modifier = Modifier.fillMaxWidth(),
                        text = "Entrar sin cuenta",
                        color = MeetColors.electricBlue
                    )
                }
            }
        }
    }
}
