package com.outsidedays.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.outsidedays.AppModel
import com.outsidedays.core.*
import com.outsidedays.data.Notifications
import java.time.DayOfWeek

@Composable fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); if(detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall) }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semanticsLabel(title))
    }
}
private fun Modifier.semanticsLabel(label: String): Modifier = this.then(Modifier.semantics { contentDescription = label })

@Composable fun SettingsScreen(model: AppModel, p: Preferences) {
    val context = LocalContext.current
    var notificationRequest by rememberSaveable { mutableStateOf("timer") }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if(granted) model.prefs { if(notificationRequest == "timer") it.copy(timerNotification = true) else it.copy(reminders = true) }
        else model.message.value = "Notifications are off. Timers and every other feature still work. You can enable notifications in Android settings."
    }
    fun enableNotification(kind: String, enabled: Boolean) {
        if(enabled && Build.VERSION.SDK_INT >= 33 && !Notifications.allowed(context)) { notificationRequest = kind; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else model.prefs { if(kind == "timer") it.copy(timerNotification = enabled) else it.copy(reminders = enabled) }
    }
    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { uri -> model.export(uri, false) } }
    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> model.export(uri, true) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::previewImport) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var deleteText by rememberSaveable { mutableStateOf("") }
    Title("Make it yours", "A little comfort, just the way you like it.")
    CozyCard {
        Text("Look & feel", style = MaterialTheme.typography.titleLarge)
        Choices(listOf("System", "Light", "Dark"), p.theme) { value -> model.prefs { it.copy(theme = value) } }
        Choices(listOf("Sage", "Sky", "Lavender", "Peach"), p.accent) { value -> model.prefs { it.copy(accent = value) } }
        SettingSwitch("Reduced motion", "Keep the scenery still and transitions instant.", p.reducedMotion) { value -> model.prefs { it.copy(reducedMotion = value) } }
        SettingSwitch("Gentle haptics", "A tiny response to game controls.", p.haptics) { value -> model.prefs { it.copy(haptics = value) } }
        SettingSwitch("Game sounds", "Soft local tones. Off by default; mute anytime.", p.sound) { value -> model.prefs { it.copy(sound = value) } }
    }
    CozyCard {
        Text("Gentle wishes", style = MaterialTheme.typography.titleLarge)
        Text("Goals are invitations, never obligations. Set 0 to turn one off.")
        NumberSetting("Daily minutes", p.dailyGoal, 0..1440) { value -> model.prefs { it.copy(dailyGoal = value) } }
        NumberSetting("Weekly minutes", p.weeklyGoal, 0..10080) { value -> model.prefs { it.copy(weeklyGoal = value) } }
        Text("First day of the week")
        Choices((1..7).map { DayOfWeek.of(it).name.lowercase().replaceFirstChar(Char::titlecase) }, DayOfWeek.of(p.firstDay).name.lowercase().replaceFirstChar(Char::titlecase)) { value -> model.prefs { it.copy(firstDay = DayOfWeek.valueOf(value.uppercase()).value) } }
    }
    CozyCard {
        Text("Quiet reminders", style = MaterialTheme.typography.titleLarge)
        SettingSwitch("Timer notification", "Optional controls while outside. Android may let you dismiss it; tracking does not depend on it.", p.timerNotification) { enableNotification("timer", it) }
        SettingSwitch("Daily invitation", "A gentle, approximate local reminder. Android may delay it to save power.", p.reminders) { enableNotification("reminder", it) }
        NumberSetting("Reminder hour (0–23)", p.reminderHour, 0..23) { value -> model.prefs { it.copy(reminderHour = value) } }
        Text("Reminders use the device’s local timezone when scheduled. Reopening the app refreshes the schedule after travel. No exact-alarm or location permission.", style = MaterialTheme.typography.bodySmall)
    }
    CozyCard {
        Text("Your data belongs here", style = MaterialTheme.typography.titleLarge)
        Text("All sessions, game progress, and preferences live on this device. No accounts, tracking, ads, or network permission. Uninstalling or clearing app storage removes them. Android cloud backup is disabled; export a JSON backup to protect your memories.")
        Text("JSON includes all completed sessions, games, and preferences. Finish the active timer first. Import validates the file, then asks before replacing local data. A CSV contains completed sessions with active duration; pauses are excluded.")
        OutlinedButton(onClick = { jsonExport.launch("outside-days-backup.json") }) { Text("Export JSON backup") }
        OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Import JSON backup") }
        OutlinedButton(onClick = { csvExport.launch("outside-days-sessions.csv") }) { Text("Export sessions as CSV") }
        Text("Reporting timezone: ${p.zone}. It is fixed on first launch and restored from backups, so travel never reshuffles historical totals. Overlapping time is counted only once within each reported activity.", style = MaterialTheme.typography.bodySmall)
        Text("Timer reliability: screen locks and process death do not stop the clock. A device restart pauses tracking at its last saved checkpoint; uncertain time is clearly flagged for review. Device clock edits do not change an already-running interval’s elapsed duration.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { deleting = true; deleteText = "" }) { Text("Delete all data", color = MaterialTheme.colorScheme.error) }
    }
    if(deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Erase Outside Days?") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("This stops the timer and permanently deletes all sessions, games, and preferences. Export a backup first if you want to keep them. Type DELETE to confirm."); OutlinedTextField(deleteText, { deleteText = it }, label = { Text("Confirmation") }) }
    }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }, confirmButton = { TextButton(enabled = deleteText == "DELETE", onClick = { model.run { model.repo.deleteAll() }; deleting = false }) { Text("Erase everything") } })
}
@Composable fun NumberSetting(label: String, current: Int, range: IntRange, save: (Int) -> Unit) {
    var text by rememberSaveable(current) { mutableStateOf(current.toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(text, { text = it.filter(Char::isDigit).take(5) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f), isError = text.toIntOrNull() !in range)
        TextButton(onClick = { text.toIntOrNull()?.let(save) }, enabled = text.toIntOrNull() in range && text != current.toString()) { Text("Save") }
    }
}
