package com.elysium369.meet.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.elysium369.meet.core.backup.GoogleDriveBackupManager
import com.elysium369.meet.ui.components.EliteButton
import com.elysium369.meet.ui.components.EliteCard
import com.elysium369.meet.ui.components.EliteTopAppBar
import com.elysium369.meet.ui.components.PhantomSectionHeader
import com.elysium369.meet.ui.theme.MeetColors
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupManager = remember { GoogleDriveBackupManager(context) }

    var signedInAccount by remember { mutableStateOf(backupManager.getSignedInAccount()) }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var lastBackupTime by remember {
        mutableStateOf(
            context.getSharedPreferences("meet_backup_prefs", Context.MODE_PRIVATE)
                .getLong("last_backup_time", 0L)
        )
    }
    var remoteBackupTime by remember { mutableStateOf<Long?>(null) }
    var autoBackupFrequency by remember {
        mutableStateOf(
            context.getSharedPreferences("meet_backup_prefs", Context.MODE_PRIVATE)
                .getString("auto_backup_frequency", "manual") ?: "manual"
        )
    }
    var frequencyExpanded by remember { mutableStateOf(false) }

    val formattedLocalBackupTime = remember(lastBackupTime) {
        if (lastBackupTime > 0L) {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            sdf.format(Date(lastBackupTime))
        } else {
            "Ninguno"
        }
    }

    val formattedRemoteBackupTime = remember(remoteBackupTime) {
        remoteBackupTime?.let {
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            sdf.format(Date(it))
        } ?: "Buscando o no disponible"
    }

    // Google Sign-In options & launcher
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            Toast.makeText(context, "Conectado a Google: ${account.email}", Toast.LENGTH_SHORT).show()
            
            // Check if backup exists on Google Drive
            coroutineScope.launch {
                backupManager.checkRemoteBackup().onSuccess { time ->
                    remoteBackupTime = time
                }
            }
        } catch (e: ApiException) {
            Toast.makeText(context, "Error de inicio de sesión: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Check remote backup status on launch if already signed in
    LaunchedEffect(signedInAccount) {
        if (signedInAccount != null) {
            backupManager.checkRemoteBackup().onSuccess { time ->
                remoteBackupTime = time
            }
        }
    }

    Scaffold(
        topBar = {
            EliteTopAppBar(
                title = "Copia de Seguridad\nGoogle Drive Backup",
                onBackClick = { navController.popBackStack() },
                backgroundColor = MeetColors.backgroundDark
            )
        },
        containerColor = MeetColors.backgroundDark
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ============================================================
            //  SECCIÓN 1: CUENTA DE GOOGLE
            // ============================================================
            item {
                Column {
                    PhantomSectionHeader(label = "CUENTA DE GOOGLE", accentColor = MeetColors.electricBlue)
                    Spacer(modifier = Modifier.height(8.dp))
                    EliteCard(
                        modifier = Modifier.fillMaxWidth(),
                        glowColor = MeetColors.electricBlue,
                        backgroundColor = MeetColors.backgroundDeep,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (signedInAccount != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = "Connected",
                                        tint = MeetColors.neonGreen,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Estado: Conectado", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(signedInAccount?.email ?: "", color = MeetColors.textSecondary, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                EliteButton(
                                    onClick = {
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            signedInAccount = null
                                            remoteBackupTime = null
                                            Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    text = "Cerrar Sesión Google",
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    color = MeetColors.error
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudQueue,
                                        contentDescription = "Disconnected",
                                        tint = MeetColors.textSecondary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Estado: Desconectado", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("Conéctate para respaldar tus vehículos y reportes.", color = MeetColors.textSecondary, fontSize = 12.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                EliteButton(
                                    onClick = {
                                        signInLauncher.launch(googleSignInClient.signInIntent)
                                    },
                                    text = "Conectar Cuenta Google",
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    color = MeetColors.electricBlue
                                )
                            }
                        }
                    }
                }
            }

            // ============================================================
            //  SECCIÓN 2: ESTADO DEL RESPALDO
            // ============================================================
            if (signedInAccount != null) {
                item {
                    Column {
                        PhantomSectionHeader(label = "RESPALDO Y RESTAURACIÓN", accentColor = MeetColors.neonGreen)
                        Spacer(modifier = Modifier.height(8.dp))
                        EliteCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = MeetColors.neonGreen,
                            backgroundColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                SettingsRow("Última copia local", formattedLocalBackupTime)
                                SettingsRow("Última copia en Drive", formattedRemoteBackupTime)

                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        EliteButton(
                                            onClick = {
                                                isBackingUp = true
                                                coroutineScope.launch {
                                                    val res = backupManager.performBackup()
                                                    isBackingUp = false
                                                    res.onSuccess {
                                                        lastBackupTime = System.currentTimeMillis()
                                                        remoteBackupTime = lastBackupTime
                                                        Toast.makeText(context, "✅ Copia subida con éxito", Toast.LENGTH_SHORT).show()
                                                    }.onFailure { err ->
                                                        Toast.makeText(context, "❌ Error de copia: ${err.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            text = if (isBackingUp) "Guardando..." else "Respaldar Ahora",
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            color = MeetColors.neonGreen,
                                            isEnabled = !isBackingUp && !isRestoring
                                        )
                                    }

                                    Box(modifier = Modifier.weight(1f)) {
                                        EliteButton(
                                            onClick = {
                                                showRestoreConfirmation = true
                                            },
                                            text = if (isRestoring) "Restaurando..." else "Restaurar Copia",
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            color = MeetColors.cyberCyan,
                                            isEnabled = !isBackingUp && !isRestoring && remoteBackupTime != null
                                        )
                                    }
                                }

                                if (isBackingUp || isRestoring) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MeetColors.electricBlue,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (isBackingUp) "Comprimiendo y subiendo base de datos..." else "Descargando e importando base de datos...",
                                            color = MeetColors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ============================================================
                //  SECCIÓN 3: CONFIGURACIÓN AUTOMÁTICA
                // ============================================================
                item {
                    Column {
                        PhantomSectionHeader(label = "COPIAS AUTOMÁTICAS", accentColor = MeetColors.cyberCyan)
                        Spacer(modifier = Modifier.height(8.dp))
                        EliteCard(
                            modifier = Modifier.fillMaxWidth(),
                            glowColor = MeetColors.cyberCyan,
                            backgroundColor = MeetColors.backgroundDeep,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Frecuencia de Auto-Respaldo", color = Color.White, fontWeight = FontWeight.Medium)
                                Text("Las copias automáticas ocurren en segundo plano cuando estás conectado a Wi-Fi.", color = MeetColors.textSecondary, fontSize = 11.sp)
                                
                                Spacer(modifier = Modifier.height(12.dp))

                                val frequencies = listOf(
                                    "manual" to "Solo Manual (Desactivado)",
                                    "daily" to "Diario",
                                    "weekly" to "Semanal"
                                )

                                ExposedDropdownMenuBox(
                                    expanded = frequencyExpanded,
                                    onExpandedChange = { frequencyExpanded = !frequencyExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = frequencies.find { it.first == autoBackupFrequency }?.second ?: "Solo Manual",
                                        onValueChange = {},
                                        readOnly = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = frequencyExpanded) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = MeetColors.cyberCyan,
                                            unfocusedBorderColor = MeetColors.cyberCyan.copy(alpha = 0.3f),
                                            focusedContainerColor = MeetColors.backgroundDeep,
                                            unfocusedContainerColor = MeetColors.backgroundDeep
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = frequencyExpanded,
                                        onDismissRequest = { frequencyExpanded = false }
                                    ) {
                                        frequencies.forEach { (key, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    autoBackupFrequency = key
                                                    frequencyExpanded = false
                                                    context.getSharedPreferences("meet_backup_prefs", Context.MODE_PRIVATE)
                                                        .edit()
                                                        .putString("auto_backup_frequency", key)
                                                        .apply()
                                                    
                                                    // Trigger WorkManager scheduling changes
                                                    Toast.makeText(context, "Frecuencia actualizada: $label", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Confirm Restore Dialog
    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            containerColor = MeetColors.backgroundDeep,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.SettingsBackupRestore, contentDescription = "Restaurar", tint = MeetColors.warning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("¿Restaurar copia?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "Esta acción sobrescribirá todos los datos actuales del dispositivo con la copia respaldada en Google Drive el día $formattedRemoteBackupTime. No se puede deshacer.",
                    color = MeetColors.textSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmation = false
                        isRestoring = true
                        coroutineScope.launch {
                            val res = backupManager.performRestore()
                            isRestoring = false
                            res.onSuccess { success ->
                                if (success) {
                                    Toast.makeText(context, "✅ Datos restaurados. Se reiniciará la app.", Toast.LENGTH_LONG).show()
                                    // Give time for Toast then restart/exit
                                    kotlinx.coroutines.delay(2000)
                                    System.exit(0)
                                } else {
                                    Toast.makeText(context, "❌ Error al descomprimir copia.", Toast.LENGTH_LONG).show()
                                }
                            }.onFailure { err ->
                                Toast.makeText(context, "❌ Error de restauración: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("SÍ, RESTAURAR", color = MeetColors.warning, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmation = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            }
        )
    }
}
