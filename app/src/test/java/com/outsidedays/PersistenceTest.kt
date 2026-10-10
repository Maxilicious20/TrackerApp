package com.outsidedays

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.outsidedays.core.*
import com.outsidedays.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.serialization.encodeToString

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PersistenceTest {
    @Test fun roomSurvivesCloseAndReopenWithPausedTimerAndGame() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "persistence-test.db"; context.deleteDatabase(name)
        var db = Room.databaseBuilder(context,LocalDatabase::class.java,name).build()
        val session = TimerEngine.pause(TimerEngine.start("one","Outside","Park",ClockSample(1000,100,1)),ClockSample(3000,2100,1))
        val game = Games.merge(Games.new("Cozy Merge"),1)
        db.dao().save(SessionRow(session.id,Backups.json.encodeToString(session)))
        db.dao().saveGame(GameRow(game.kind,Backups.json.encodeToString(game)))
        db.close()
        db = Room.databaseBuilder(context,LocalDatabase::class.java,name).build()
        assertEquals(session,Backups.json.decodeFromString<Session>(db.dao().sessions().single().payload))
        assertEquals(game,Backups.json.decodeFromString<Game>(db.dao().games().single().payload))
        db.close(); context.deleteDatabase(name); Unit
    }
    @Test fun repositoryPreventsConcurrentTimersAndValidatesBeforeReplacingData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context,LocalDatabase::class.java).build()
        val repo = Repository(context,db); repo.initialize()
        val outcomes = (1..8).map { async(Dispatchers.IO) { runCatching { repo.start("Outside","Walk") }.isSuccess } }.awaitAll()
        assertEquals(1,outcomes.count { it }); assertEquals(1,repo.sessions.first().size)
        repo.action("pause"); assertEquals("paused",repo.sessions.first().single().state)
        repo.action("finish")
        val backup = repo.backup()
        try { repo.importBackup(backup.copy(version=99)); fail("Invalid backup accepted") } catch(_: IllegalArgumentException) { }
        assertEquals(backup.sessions,repo.sessions.first())
        repo.updatePreferences { it.copy(accent="Lavender") }
        assertEquals("Lavender",repo.preferences.first().accent)
        repo.importBackup(backup.copy(sessions=listOf(Session("imported",spans=listOf(Span(1000,2000))))))
        assertEquals("imported",repo.sessions.first().single().id)
        db.close()
    }
}
