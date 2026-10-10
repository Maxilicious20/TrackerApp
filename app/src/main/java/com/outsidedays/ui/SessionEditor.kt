@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.outsidedays.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.outsidedays.core.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun SessionEditor(session: Session, zoneName: String, onDismiss: () -> Unit, onSave: (Session) -> Unit) {
    val zone = ZoneId.of(zoneName); val format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)
    fun display(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(format)
    var activity by rememberSaveable(session.id) { mutableStateOf(session.activity) }
    var category by rememberSaveable(session.id) { mutableStateOf(session.category) }
    var notes by rememberSaveable(session.id) { mutableStateOf(session.notes) }
    var mood by rememberSaveable(session.id) { mutableStateOf(session.mood) }
    var starts by rememberSaveable(session.id) { mutableStateOf(session.spans.map { display(it.start) }) }
    var ends by rememberSaveable(session.id) { mutableStateOf(session.spans.map { display(it.end) }) }
    var error by rememberSaveable(session.id) { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), modifier = Modifier.padding(16.dp).widthIn(max = 620.dp).fillMaxWidth().heightIn(max = 760.dp)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Keep this little memory", style = MaterialTheme.typography.headlineMedium)
                Text("Mood and notes are optional. Each interval below counts as active time; gaps stay paused.")
                Choices(listOf("Outside", "Reading", "Focus", "Relaxing"), activity) { activity = it }
                OutlinedTextField(category, { category = it.take(80) }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
                Choices(listOf("Peaceful", "Happy", "Energized", "Okay", "Low"), mood) { mood = if(mood == it) "" else it }
                OutlinedTextField(notes, { notes = it.take(4000) }, label = { Text("A thought to remember") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("Active intervals · $zoneName", style = MaterialTheme.typography.titleMedium)
                Text("Use YYYY-MM-DD HH:MM:SS. On an autumn clock overlap, unchanged timestamps retain their offset; edited timestamps use the earlier offset. Nonexistent spring times are rejected.", style = MaterialTheme.typography.bodySmall)
                starts.indices.forEach { i ->
                    Text("Interval ${i + 1}")
                    OutlinedTextField(starts[i], { value -> starts = starts.toMutableList().apply { this[i] = value } }, label = { Text("Start") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(ends[i], { value -> ends = ends.toMutableList().apply { this[i] = value } }, label = { Text("End") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TextButton(onClick = { starts = starts.filterIndexed { j, _ -> j != i }; ends = ends.filterIndexed { j, _ -> j != i } }) { Text("Remove interval ${i + 1}") }
                }
                TextButton(onClick = { val now = System.currentTimeMillis(); starts = starts + display(now - 60_000); ends = ends + display(now) }) { Text("+ Add active interval") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Button(onClick = {
                        try {
                            fun parse(value: String, original: Long?): Long {
                                if(original != null && display(original) == value) return original
                                val local = LocalDateTime.parse(value, format)
                                require(zone.rules.getValidOffsets(local).isNotEmpty()) { "That local time does not exist because of daylight saving time." }
                                return local.atZone(zone).toInstant().toEpochMilli()
                            }
                            val spans = starts.indices.map { i ->
                                val a = parse(starts[i], session.spans.getOrNull(i)?.start); val b = parse(ends[i], session.spans.getOrNull(i)?.end)
                                require(b > a) { "Each end must be after its start." }; require(session.spans.getOrNull(i)?.end == b || b <= System.currentTimeMillis() + 60_000) { "A completed session cannot end in the future." }; Span(a, b)
                            }
                            val result = session.copy(activity = activity, category = category.ifBlank { activity }, mood = mood, notes = notes, spans = union(spans), state = "complete")
                            Backups.validate(Backup(sessions = listOf(result), games = emptyList(), preferences = Preferences(zone = zoneName)))
                            onSave(result)
                        } catch(e: Exception) { error = e.message ?: "Check the date and time format." }
                    }) { Text("Save memory") }
                }
            }
        }
    }
}
