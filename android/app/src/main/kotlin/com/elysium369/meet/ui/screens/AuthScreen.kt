package com.elysium369.meet.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit, onOfflineMode: () -> Unit) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("MEET", style = MaterialTheme.typography.displayLarge, color = Color(0xFF39FF14), fontWeight = FontWeight.Black)
            Text("Mecánicos Especialistas En Todo", color = Color(0xFF39FF14).copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 32.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF39FF14), unfocusedBorderColor = Color.DarkGray)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Contraseña", color = Color.Gray) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF39FF14), unfocusedBorderColor = Color.DarkGray)
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { loading = true; onAuthSuccess() },
                modifier = Modifier.fillMaxWidth().height(50.dp).border(1.dp, Color(0xFF39FF14), RoundedCornerShape(8.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A0E1A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (loading) CircularProgressIndicator(color = Color(0xFF39FF14), modifier = Modifier.size(24.dp))
                else Text(if (isLogin) "INICIAR SESIÓN" else "REGISTRARSE", fontWeight = FontWeight.Bold, color = Color(0xFF39FF14))
            }

            TextButton(onClick = { isLogin = !isLogin }) {
                Text(if (isLogin) "¿No tienes cuenta? Regístrate" else "¿Ya tienes cuenta? Inicia sesión", color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color(0xFF39FF14).copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onOfflineMode,
                modifier = Modifier.fillMaxWidth().height(50.dp).border(1.dp, Color(0xFFCC00FF).copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF0A0E1A))
            ) {
                Text("Entrar sin cuenta (Modo Offline)", color = Color(0xFFCC00FF), fontWeight = FontWeight.Bold)
            }
        }
    }
}
