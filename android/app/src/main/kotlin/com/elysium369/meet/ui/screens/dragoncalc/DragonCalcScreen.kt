package com.elysium369.meet.ui.screens.dragoncalc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dragoncore.evaluator.DragonContext
import dragoncore.evaluator.DragonEvaluator
import dragoncore.lexer.DragonLexer
import dragoncore.parser.DragonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Source engine: DragonCalc-Elysium-Vanguard. The MEET surface owns navigation. */
@Composable
fun DragonCalcScreen(onBack: () -> Unit) {
    val context = remember { DragonContext() }
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var output by rememberSaveable { mutableStateOf("Escribe una expresión") }
    var angleMode by rememberSaveable { mutableStateOf(DragonContext.AngleMode.DEGREE) }
    var busy by remember { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(listOf<String>()) }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF07101F)).verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onBack) { Text("Atrás") }
            OutlinedButton(onClick = {
                angleMode = if (angleMode == DragonContext.AngleMode.DEGREE)
                    DragonContext.AngleMode.RADIAN else DragonContext.AngleMode.DEGREE
            }) { Text(if (angleMode == DragonContext.AngleMode.DEGREE) "DEG" else "RAD") }
        }
        Text("DragonCalc · Elysium", color = Color(0xFF53E6F5), fontWeight = FontWeight.Bold)
        Text("Calculadora científica", color = Color.White)
        OutlinedTextField(
            value = input,
            onValueChange = { if (it.length <= 512) input = it },
            label = { Text("Expresión") },
            placeholder = { Text("sin(30) + 2^3") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("7", "8", "9", "/", "sin(").forEach { token ->
                OutlinedButton(onClick = { if (input.length + token.length <= 512) input += token },
                    modifier = Modifier.weight(1f)) { Text(token) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("4", "5", "6", "*", "cos(").forEach { token ->
                OutlinedButton(onClick = { if (input.length + token.length <= 512) input += token },
                    modifier = Modifier.weight(1f)) { Text(token) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("1", "2", "3", "-", "tan(").forEach { token ->
                OutlinedButton(onClick = { if (input.length + token.length <= 512) input += token },
                    modifier = Modifier.weight(1f)) { Text(token) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("0", ".", "(", ")", "+").forEach { token ->
                OutlinedButton(onClick = { if (input.length + token.length <= 512) input += token },
                    modifier = Modifier.weight(1f)) { Text(token) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { input = "" }, modifier = Modifier.weight(1f)) { Text("C") }
            OutlinedButton(onClick = { input = input.dropLast(1) }, modifier = Modifier.weight(1f)) { Text("⌫") }
            Button(onClick = {
                val expression = input.trim()
                if (expression.isBlank() || busy) return@Button
                busy = true
                scope.launch {
                    output = try {
                        withTimeout(2_000L) {
                            withContext(Dispatchers.Default) {
                                val tokens = DragonLexer().tokenize(expression)
                                require(tokens.size <= 256) { "Expresión demasiado compleja" }
                                val parsed = DragonParser(tokens).parse()
                                DragonCalcExpressionPolicy.requireInteractive(parsed)
                                context.angleMode = angleMode
                                val value = DragonEvaluator().evaluate(parsed, context)
                                require(value.isFinite()) { "Resultado no finito" }
                                value.toString()
                            }
                        }
                    } catch (_: TimeoutCancellationException) {
                        "El cálculo tardó demasiado"
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: IllegalArgumentException) {
                        failure.message?.take(100) ?: "Expresión inválida"
                    } catch (failure: dragoncore.lexer.LexerException) {
                        failure.message?.take(100) ?: "Expresión inválida"
                    } catch (failure: dragoncore.parser.ParserException) {
                        failure.message?.take(100) ?: "Expresión inválida"
                    } catch (failure: dragoncore.evaluator.EvaluatorException) {
                        failure.message?.take(100) ?: "No se pudo calcular"
                    }
                    if (output.toDoubleOrNull() != null) history = (listOf("$expression = $output") + history).take(12)
                    busy = false
                }
            }, enabled = !busy, modifier = Modifier.weight(2f)) { Text(if (busy) "Calculando…" else "=") }
        }
        Text(output, color = Color(0xFF83F5B8), fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold)
        if (history.isNotEmpty()) {
            Text("Historial de esta sesión", color = Color(0xFF53E6F5))
            history.forEach { Text(it, color = Color.White, fontFamily = FontFamily.Monospace) }
        }
    }
}
