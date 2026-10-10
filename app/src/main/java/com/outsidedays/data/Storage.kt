package com.outsidedays.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import com.outsidedays.core.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.util.UUID

@Entity(tableName = "sessions") data class SessionRow(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "games") data class GameRow(@PrimaryKey val kind: String, val payload: String)
@Dao interface LocalDao {
    @Query("SELECT * FROM sessions") fun observeSessions(): Flow<List<SessionRow>>
    @Query("SELECT * FROM games") fun observeGames(): Flow<List<GameRow>>
    @Query("SELECT * FROM sessions") suspend fun sessions(): List<SessionRow>
    @Query("SELECT * FROM games") suspend fun games(): List<GameRow>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(row: SessionRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGame(row: GameRow)
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM games") suspend fun clearGames()
}
@Database(entities = [SessionRow::class, GameRow::class], version = 1, exportSchema = false)
abstract class LocalDatabase : RoomDatabase() { abstract fun dao(): LocalDao }
private val Context.settings by preferencesDataStore("outside_preferences")
class Repository(val context: Context, val db: LocalDatabase = Room.databaseBuilder(context, LocalDatabase::class.java, "outside-days.db").build()) {
    private val mutex = Mutex()
    private val json = Backups.json
    private val key = stringPreferencesKey("preferences_v1")
    val preferences: Flow<Preferences> = context.settings.data.map { data -> data[key]?.let { json.decodeFromString<Preferences>(it) } ?: Preferences() }
    val sessions = db.dao().observeSessions().map { rows -> rows.map { json.decodeFromString<Session>(it.payload) }.sortedByDescending { it.created } }
    val games = db.dao().observeGames().map { rows -> rows.associate { it.kind to json.decodeFromString<Game>(it.payload) } }
    fun clock() = ClockSample(System.currentTimeMillis(), SystemClock.elapsedRealtime(), Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1))
    private suspend fun rows() = db.dao().sessions().map { json.decodeFromString<Session>(it.payload) }
    private suspend fun put(s: Session) = db.dao().save(SessionRow(s.id, json.encodeToString(s)))
    suspend fun initialize() {
        context.settings.edit { if (it[key] == null) it[key] = json.encodeToString(Preferences()) }
        recover()
    }
    suspend fun recover() = mutex.withLock { rows().filter { it.state != "complete" }.forEach { put(TimerEngine.restore(it, clock())) } }
    suspend fun start(activity: String, category: String) = mutex.withLock {
        require(rows().none { it.state != "complete" }) { "Finish the current activity before starting another." }
        put(TimerEngine.start(UUID.randomUUID().toString(), activity, category.take(80), clock()))
    }
    suspend fun action(action: String): Session? = mutex.withLock {
        val current = rows().firstOrNull { it.state != "complete" } ?: return@withLock null
        val s = TimerEngine.restore(current, clock())
        val next = when(action) {
            "pause" -> TimerEngine.pause(s, clock())
            "resume" -> if (s.state == "paused" || s.state == "interrupted") TimerEngine.resume(s, clock()) else s
            "finish" -> TimerEngine.finish(s, clock())
            "checkpoint" -> TimerEngine.checkpoint(s, clock())
            else -> s
        }
        put(next); next
    }
    suspend fun saveSession(s: Session) = mutex.withLock {
        Backups.validate(Backup(sessions = listOf(s), games = emptyList(), preferences = preferences.first()))
        require(rows().none { it.id == s.id && it.state != "complete" }) { "Finish the activity before editing." }
        put(s)
    }
    suspend fun delete(id: String) = mutex.withLock { require(rows().none { it.id == id && it.state != "complete" }); db.dao().delete(id) }
    suspend fun updatePreferences(transform: (Preferences) -> Preferences) {
        context.settings.edit { values ->
            val p = values[key]?.let { json.decodeFromString<Preferences>(it) } ?: Preferences()
            val next = transform(p); Backups.validatePreferences(next); values[key] = json.encodeToString(next)
        }
    }
    suspend fun changeGame(kind: String, transform: (Game?) -> Game) = mutex.withLock {
        val old = db.dao().games().firstOrNull { it.kind == kind }?.let { json.decodeFromString<Game>(it.payload) }
        val next = transform(old)
        db.dao().saveGame(GameRow(kind, json.encodeToString(next)))
    }
    suspend fun backup(): Backup = mutex.withLock {
        val all = rows(); require(all.none { it.state != "complete" }) { "Finish your activity before exporting a full backup." }
        Backup(sessions = all, games = db.dao().games().map { json.decodeFromString<Game>(it.payload) }, preferences = preferences.first())
    }
    suspend fun importBackup(backup: Backup) = mutex.withLock {
        Backups.validate(backup)
        require(rows().none { it.state != "complete" }) { "Finish your current activity before importing." }
        val oldPreferences = preferences.first()
        // Validate first, then compensate preferences if the Room transaction fails.
        updatePreferences { backup.preferences.copy(timerNotification = false, reminders = false) }
        try {
            db.withTransaction {
                db.dao().clearSessions(); db.dao().clearGames()
                backup.sessions.forEach { put(it) }
                backup.games.forEach { db.dao().saveGame(GameRow(it.kind, json.encodeToString(it))) }
            }
        } catch (e: Exception) { updatePreferences { oldPreferences }; throw e }
    }
    suspend fun deleteAll() = mutex.withLock {
        db.withTransaction { db.dao().clearSessions(); db.dao().clearGames() }
        updatePreferences { Preferences(introSeen = true) }
    }
}
