package com.elysium369.meet.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium369.meet.core.terminal.ElysiumInteractivePtySession
import com.elysium369.meet.ui.theme.MeetColors

/**
 * Pro Touch Bar & Extra Keys Navigation Row for Elysium Vanguard Terminal.
 * Supports:
 * - Direct Clipboard Paste (PEGAR) & Screen Copy (COPIAR)
 * - Sticky Ctrl/Alt locks
 * - Directional Arrows D-Pad (▲ ▼ ◄ ►)
 * - Standard Unix signals (C-c, C-d, C-z, C-l, C-r, C-a, C-e)
 * - Quick symbols row
 */
@Composable
fun TerminalTouchBar(
    session: ElysiumInteractivePtySession,
    modifier: Modifier = Modifier
) {
    val isCtrl by session.isCtrlActive.collectAsState()
    val isAlt by session.isAltActive.collectAsState()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        // ═══ PRIMARY MODIFIER & NAVIGATION ROW (Touch Bar) ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PASTE button (Reads from Android Clipboard)
            TerminalKeyButton(
                label = "📋 PEGAR",
                onClick = {
                    try {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).coerceToText(context).toString()
                            if (text.isNotEmpty()) {
                                session.writeString(text)
                                Toast.makeText(context, "Texto pegado en terminal", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Portapapeles vacío", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al pegar: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                activeColor = MeetColors.neonGreen
            )

            // ESC key
            TerminalKeyButton(
                label = "ESC",
                onClick = { session.sendEsc() },
                activeColor = MeetColors.warning
            )

            // Sticky CTRL toggle
            TerminalKeyButton(
                label = "CTRL",
                onClick = { session.toggleCtrl() },
                isActive = isCtrl,
                activeColor = MeetColors.neonGreen
            )

            // Sticky ALT toggle
            TerminalKeyButton(
                label = "ALT",
                onClick = { session.toggleAlt() },
                isActive = isAlt,
                activeColor = Color(0xFFBD00FF)
            )

            // TAB key
            TerminalKeyButton(
                label = "TAB",
                onClick = { session.sendTab() },
                activeColor = MeetColors.cyberCyan
            )

            // D-Pad Directional Arrows
            TerminalKeyButton(
                label = "▲",
                onClick = { session.sendArrowUp() },
                activeColor = MeetColors.cyberCyan
            )
            TerminalKeyButton(
                label = "▼",
                onClick = { session.sendArrowDown() },
                activeColor = MeetColors.cyberCyan
            )
            TerminalKeyButton(
                label = "◄",
                onClick = { session.sendArrowLeft() },
                activeColor = MeetColors.cyberCyan
            )
            TerminalKeyButton(
                label = "►",
                onClick = { session.sendArrowRight() },
                activeColor = MeetColors.cyberCyan
            )

            // Common POSIX Control Signals
            TerminalKeyButton(
                label = "C-c",
                onClick = { session.sendCtrlC() },
                activeColor = MeetColors.error
            )
            TerminalKeyButton(
                label = "C-d",
                onClick = { session.sendCtrlD() },
                activeColor = MeetColors.textSecondary
            )
            TerminalKeyButton(
                label = "C-z",
                onClick = { session.sendCtrlZ() },
                activeColor = MeetColors.warning
            )
            TerminalKeyButton(
                label = "C-l",
                onClick = { session.sendCtrlL() },
                activeColor = MeetColors.cyberCyan
            )
            TerminalKeyButton(
                label = "C-r",
                onClick = { session.sendCtrlR() },
                activeColor = Color(0xFFFFB86C)
            )
            TerminalKeyButton(
                label = "C-a",
                onClick = { session.sendCtrlA() },
                activeColor = MeetColors.textSecondary
            )
            TerminalKeyButton(
                label = "C-e",
                onClick = { session.sendCtrlE() },
                activeColor = MeetColors.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ═══ SECONDARY SYMBOLS & EDITING ROW ═══
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val symbols = listOf(
                "|" to "|",
                "~" to "~",
                "/" to "/",
                "-" to "-",
                "_" to "_",
                "$" to "$",
                ":" to ":",
                ";" to ";",
                "=" to "=",
                "\\" to "\\",
                "&" to "&",
                "<" to "<",
                ">" to ">",
                "\"" to "\"",
                "'" to "'",
                "`" to "`"
            )

            symbols.forEach { (sym, text) ->
                TerminalKeyButton(
                    label = sym,
                    onClick = { session.writeString(text) },
                    activeColor = MeetColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun TerminalKeyButton(
    label: String,
    onClick: () -> Unit,
    isActive: Boolean = false,
    activeColor: Color = MeetColors.cyberCyan
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) activeColor.copy(alpha = 0.35f) else MeetColors.backgroundDeep,
            contentColor = if (isActive) activeColor else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) activeColor else MeetColors.textMuted.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        modifier = Modifier
            .height(34.dp)
            .defaultMinSize(minWidth = 38.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}
