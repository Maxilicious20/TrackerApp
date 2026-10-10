package com.outsidedays

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.outsidedays.core.*
import com.outsidedays.data.Notifications
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as OutsideApp).repository
    val message = MutableStateFlow<String?>(null)
    val sessions = repo.sessions.catch { message.value = "Could not read sessions: ${it.message}"; emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val games = repo.games.catch { message.value = "Could not read games: ${it.message}"; emit(emptyMap()) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val preferences = repo.preferences.catch { message.value = "Could not read preferences: ${it.message}"; emit(Preferences()) }.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    val ready = MutableStateFlow(false)
    val pendingImport = MutableStateFlow<Backup?>(null)
    init {
        initialize()
        run { combine(repo.sessions, repo.preferences) { s, p -> s to p }.collect { (s, p) ->
            Notifications.timer(app, s.firstOrNull { it.state != "complete" }, p.timerNotification, repo.clock())
        } }
        run { repo.preferences.map { it.reminders to it.reminderHour }.distinctUntilChanged().collect { Notifications.schedule(app, repo.preferences.first()) } }
    }
    fun run(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try { block() } catch (e: Exception) { if (e is CancellationException) throw e; message.value = e.message?.take(240) ?: "Something went wrong. Please try again." }
    }
    fun initialize() = run { repo.initialize(); ready.value = true }
    fun start(activity: String, category: String) = run { repo.start(activity, category) }
    fun action(action: String, finished: ((Session) -> Unit)? = null) = run {
        val s = repo.action(action)
        if (s != null && finished != null) withContext(Dispatchers.Main) { finished(s) }
    }
    fun prefs(transform: (Preferences) -> Preferences) = run { repo.updatePreferences(transform) }
    fun game(kind: String, transform: (Game?) -> Game) = run { repo.changeGame(kind, transform) }
    fun export(uri: Uri, csv: Boolean) = run {
        val text = if (csv) Backups.csv(repo.sessions.first()) else Backups.encode(repo.backup())
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } ?: error("Unable to write this document")
        message.value = if(csv) "CSV exported." else "Backup saved. Keep a copy somewhere safe."
    }
    fun previewImport(uri: Uri) = run {
        val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n == -1) break; require(out.size() + n <= Backups.MAX_BYTES) { "Backup exceeds 5 MB" }; out.write(buffer, 0, n) }; out.toByteArray()
        } ?: error("Unable to read this document")
        pendingImport.value = Backups.decode(bytes.toString(Charsets.UTF_8))
    }
    fun confirmImport() = run {
        pendingImport.value?.let { repo.importBackup(it) }; pendingImport.value = null
        message.value = "Backup restored. Reminders and notifications remain off until you enable them."
    }
}
