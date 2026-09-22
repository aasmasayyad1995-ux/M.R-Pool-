package com.mrpool.eightball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrpool.eightball.net.ChatFilter
import com.mrpool.eightball.net.ChatLine
import com.mrpool.eightball.net.ChatSend

/**
 * The chat for an online match.
 *
 * Three things sit beside the text box rather than buried in a menu, because a player who
 * is being pestered should not have to go looking: mute, report, and the fact that what
 * they type is filtered. The apology of a filter is admitted in the hint line — it stops
 * the lazy abuse and nothing cleverer than that — so nobody is sold more safety than is
 * really there.
 */
@Composable
fun ChatPanel(
    lines: List<ChatLine>,
    muted: Boolean,
    reported: Boolean,
    opponentName: String,
    onSend: (String) -> ChatSend,
    onToggleMute: () -> Unit,
    onReport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember { mutableStateOf("") }
    var warning by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Follow the newest line, which is what anybody reading a chat expects.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(
        modifier = modifier
            .width(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF2101719))
            .border(1.dp, Chalk.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Chat",
                color = Chalk,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            PanelAction(
                label = if (muted) "Unmute" else "Mute",
                tint = if (muted) Gold else Chalk.copy(alpha = 0.75f),
                onClick = onToggleMute
            )
            PanelAction(
                label = if (reported) "Reported" else "Report",
                tint = if (reported) Chalk.copy(alpha = 0.4f) else Crimson,
                onClick = { if (!reported) onReport() }
            )
            PanelAction(label = "✕", tint = Chalk.copy(alpha = 0.75f), onClick = onClose)
        }

        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp, max = 190.dp)) {
            if (lines.isEmpty()) {
                Text(
                    "Say hello to $opponentName",
                    color = Chalk.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    items(lines, key = { it.id }) { line -> ChatBubble(line) }
                }
            }
        }

        warning?.let {
            Text(
                it,
                color = Gold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        OutlinedTextField(
            value = draft,
            onValueChange = {
                // Cut at the limit as it is typed, so the player sees what will go rather
                // than losing the end of it silently on send.
                draft = it.take(ChatFilter.MAX_LENGTH)
                warning = null
            },
            singleLine = true,
            placeholder = {
                Text("Message", color = Chalk.copy(alpha = 0.35f), fontSize = 13.sp)
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    warning = send(draft, onSend)
                    if (warning == null) draft = ""
                }
            ),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Chalk,
                unfocusedTextColor = Chalk,
                focusedContainerColor = InkSoft,
                unfocusedContainerColor = InkSoft,
                cursorColor = Cyan,
                focusedIndicatorColor = Cyan,
                unfocusedIndicatorColor = Chalk.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Rude words are blanked out. Mute stops them reaching you.",
                color = Chalk.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f)
            )
            PanelAction(
                label = "Send",
                tint = Cyan,
                onClick = {
                    warning = send(draft, onSend)
                    if (warning == null) draft = ""
                }
            )
        }
    }
}

/** Sends, and returns what to warn the player about, or null when it went. */
private fun send(draft: String, onSend: (String) -> ChatSend): String? =
    when (val result = onSend(draft)) {
        ChatSend.Sent -> null
        ChatSend.Empty -> null
        is ChatSend.TooFast -> {
            val seconds = ((result.waitMillis + 999L) / 1000L).coerceAtLeast(1L)
            "Slow down — try again in $seconds second${if (seconds == 1L) "" else "s"}"
        }
    }

@Composable
private fun ChatBubble(line: ChatLine) {
    if (line.note) {
        // The game's own remarks, told apart from a player's words by being centred and
        // grey, so a note can never be mistaken for something the opponent said.
        Text(
            line.text,
            color = Chalk.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (line.fromLocal) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (line.fromLocal) Cyan.copy(alpha = 0.16f) else Color(0x991A2326))
                .padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            Text(
                line.author,
                color = if (line.fromLocal) Cyan else Gold.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(line.text, color = Chalk, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PanelAction(label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

/** A badge for the chat button, so a message that arrived mid shot is not missed. */
@Composable
fun UnreadDot(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Crimson)
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            if (count > 9) "9+" else count.toString(),
            color = Chalk,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
