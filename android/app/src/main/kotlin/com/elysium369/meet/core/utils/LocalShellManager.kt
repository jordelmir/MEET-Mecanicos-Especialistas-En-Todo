package com.elysium369.meet.core.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class LocalShellManager(private val scope: CoroutineScope) {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null

    private val _terminalLines = MutableStateFlow<List<String>>(
        listOf(
            "⚡ MEET Android Terminal Shell v1.0",
            "Sustituto de Termux integrado en tiempo real.",
            "Escriba comandos del sistema (ej: ls, pm list packages, getprop, df -h)",
            ""
        )
    )
    val terminalLines: StateFlow<List<String>> = _terminalLines.asStateFlow()

    init {
        startShell()
    }

    fun startShell() {
        stopShell()
        try {
            val builder = ProcessBuilder("/system/bin/sh")
                .redirectErrorStream(true)
            
            val proc = builder.start()
            process = proc
            writer = BufferedWriter(OutputStreamWriter(proc.outputStream))

            readerJob = scope.launch(Dispatchers.IO) {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        appendOutput(line ?: "")
                    }
                } catch (e: Exception) {
                    appendOutput("[Shell Error: ${e.message}]")
                } finally {
                    appendOutput("[Proceso finalizado]")
                }
            }
        } catch (e: Exception) {
            _terminalLines.update { it + "Error iniciando shell: ${e.message}" }
        }
    }

    fun executeCommand(command: String) {
        val w = writer ?: return
        scope.launch(Dispatchers.IO) {
            try {
                _terminalLines.update { it + "❯ $command" }
                w.write(command + "\n")
                w.flush()
            } catch (e: Exception) {
                _terminalLines.update { it + "[Error ejecutando: ${e.message}]" }
            }
        }
    }

    private fun appendOutput(text: String) {
        _terminalLines.update {
            val list = it.toMutableList()
            list.add(text)
            if (list.size > 1000) list.removeAt(0)
            list
        }
    }

    fun clearTerminal() {
        _terminalLines.value = listOf("❯ ")
    }

    fun stopShell() {
        readerJob?.cancel()
        try {
            writer?.close()
        } catch (_: Exception) {}
        process?.destroy()
        process = null
        writer = null
    }
}
