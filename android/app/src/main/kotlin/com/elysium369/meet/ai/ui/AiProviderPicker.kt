package com.elysium369.meet.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.elysium369.meet.ai.domain.AiProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderPicker(
    providers: List<AiProvider>,
    selectedProviderId: String,
    onProviderSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentProviderName = providers.find { it.id == selectedProviderId }?.displayName ?: selectedProviderId

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = currentProviderName,
            onValueChange = {},
            label = { Text("Proveedor de IA") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            providers.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        onProviderSelected(provider.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
