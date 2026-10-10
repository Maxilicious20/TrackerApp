package com.outsidedays

import com.outsidedays.core.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private fun sample() = Backup(sessions=listOf(Session("one",spans=listOf(Span(1000,5000)),notes="A comma, and \"quote\"\nnew line")),games=Games.names.map { Games.new(it) },preferences=Preferences())
    @Test fun completeBackupRoundTripsAllData() { val b=sample(); assertEquals(b,Backups.decode(Backups.encode(b))) }
    @Test fun rejectsUnsupportedVersionMalformedJsonAndOversizedFiles() {
        assertThrows(Exception::class.java){Backups.decode("{invalid")}
        assertThrows(Exception::class.java){Backups.decode(Backups.json.encodeToString(sample().copy(version=99)))}
        assertThrows(Exception::class.java){Backups.decode(" ".repeat(Backups.MAX_BYTES+1))}
    }
    @Test fun rejectsActiveSessionsInvalidTimesAndDuplicates() {
        val b=sample(); val s=b.sessions.single()
        listOf(b.copy(sessions=listOf(s.copy(state="running"))), b.copy(sessions=listOf(s.copy(spans=listOf(Span(10,2))))), b.copy(sessions=listOf(s,s))).forEach { invalid -> assertThrows(Exception::class.java){Backups.validate(invalid)} }
    }
    @Test fun rejectsCorruptGameAndPreferenceState() {
        val b=sample()
        assertThrows(Exception::class.java){Backups.validate(b.copy(games=listOf(Games.new("Cozy Merge").copy(board=listOf(3)))))}
        assertThrows(Exception::class.java){Backups.validate(b.copy(preferences=b.preferences.copy(zone="Mars/Olympus")))}
        assertThrows(Exception::class.java){Backups.validate(b.copy(games=listOf(Games.new("Cloud Slide").copy(board=listOf(2,1,3,4,5,6,7,8,0)))))}
    }
    @Test fun csvEscapesQuotesAndSpreadsheetFormulasAndExcludesPausedTime() {
        val text=Backups.csv(listOf(Session("x",notes="=SUM(1,2)\n\"hi\"",spans=listOf(Span(0,1000),Span(5000,7000)))))
        assertTrue(text.contains("\"3\"")); assertTrue(text.contains("\"'=SUM(1,2)")); assertTrue(text.contains("\"\"hi\"\""))
    }
}
