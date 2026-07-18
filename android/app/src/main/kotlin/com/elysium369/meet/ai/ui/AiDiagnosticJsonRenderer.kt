package com.elysium369.meet.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elysium369.meet.ai.data.AiJsonRepair
import kotlinx.serialization.json.*

@Composable
fun AiDiagnosticJsonRenderer(
    rawText: String,
    modifier: Modifier = Modifier
) {
    val jsonObject = AiJsonRepair.repairAndParse(rawText)

    if (jsonObject == null) {
        Column(modifier = modifier.fillMaxWidth()) {
            Surface(
                color = Color(0xFFFFF3CD),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Nota",
                        tint = Color(0xFF856404)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "El proveedor respondió texto no estructurado. Se mostrará como respuesta libre.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF856404)
                    )
                }
            }
            Text(
                text = rawText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        val severity = jsonObject["severity"]?.jsonPrimitive?.content ?: "UNKNOWN"
        val confidence = jsonObject["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val status = jsonObject["diagnostic_status"]?.jsonPrimitive?.content ?: "PRELIMINARY"
        val summary = jsonObject["summary"]?.jsonPrimitive?.content ?: ""

        val severityColor = when (severity) {
            "CRITICAL" -> MaterialTheme.colorScheme.error
            "HIGH" -> Color(0xFFE65100)
            "MEDIUM" -> Color(0xFFF57C00)
            else -> Color(0xFF388E3C)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Severidad: $severity",
                        style = MaterialTheme.typography.titleMedium,
                        color = severityColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Confianza: ${(confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Estado: $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        val likelyCauses = jsonObject["likely_causes"]?.jsonArray
        if (likelyCauses != null && likelyCauses.isNotEmpty()) {
            Text(
                text = "Causas Probables:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            likelyCauses.forEach { causeElement ->
                val causeObj = causeElement.jsonObject
                val cause = causeObj["cause"]?.jsonPrimitive?.content ?: ""
                val prob = causeObj["probability"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val why = causeObj["why"]?.jsonPrimitive?.content ?: ""
                val nextTest = causeObj["next_test"]?.jsonPrimitive?.content ?: ""
                val requiredTools = causeObj["required_tools"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cause,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(prob * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(text = "Explicación: $why", style = MaterialTheme.typography.bodyMedium)
                        if (nextTest.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = "Siguiente prueba: $nextTest", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        if (requiredTools.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = "Herramientas: ${requiredTools.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        val doNotReplace = jsonObject["do_not_replace_yet"]?.jsonArray
        if (doNotReplace != null && doNotReplace.isNotEmpty()) {
            Surface(
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = "Alerta", tint = Color(0xFFC62828))
                        Spacer(Modifier.width(8.dp))
                        Text("¡NO REEMPLAZAR AÚN!", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    doNotReplace.forEach { element ->
                        Text("- ${element.jsonPrimitive.content}", color = Color(0xFFC62828), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        val repairPlan = jsonObject["repair_plan"]?.jsonArray
        if (repairPlan != null && repairPlan.isNotEmpty()) {
            Text(
                text = "Plan de Reparación:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            repairPlan.forEach { stepElement ->
                val stepObj = stepElement.jsonObject
                val stepNum = stepObj["step"]?.jsonPrimitive?.intOrNull ?: 1
                val title = stepObj["title"]?.jsonPrimitive?.content ?: ""
                val proc = stepObj["procedure"]?.jsonPrimitive?.content ?: ""
                val warn = stepObj["safety_warning"]?.jsonPrimitive?.content ?: ""

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Paso $stepNum: $title",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = proc, style = MaterialTheme.typography.bodyMedium)
                        if (warn.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = "Seguridad: $warn", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        val customerExp = jsonObject["customer_explanation"]?.jsonPrimitive?.content ?: ""
        if (customerExp.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Explicación para el Cliente:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(customerExp, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
