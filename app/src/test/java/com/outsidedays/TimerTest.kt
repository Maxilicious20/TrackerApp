package com.outsidedays

import com.outsidedays.core.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class TimerTest {
    private val start = ClockSample(1_700_000_000_000, 10_000, 4)
    @Test fun pauseResumeExcludesPauseAndSurvivesSerialization() {
        var s = TimerEngine.start("test", "Outside", "Walk", start)
        s = TimerEngine.pause(s, start.copy(wall = start.wall + 15_000, elapsed = 25_000))
        assertEquals(15_000L, TimerEngine.duration(s, start.copy(elapsed = 100_000)))
        s = Backups.json.decodeFromString<Session>(Backups.json.encodeToString(s))
        s = TimerEngine.resume(s, start.copy(wall = start.wall + 90_000, elapsed = 100_000))
        val end = TimerEngine.finish(s, start.copy(wall = start.wall + 100_000, elapsed = 110_000))
        assertEquals(25_000L, TimerEngine.duration(end, start))
        assertEquals(2, end.spans.size)
        assertEquals("complete", end.state)
    }
    @Test fun backgroundAndProcessDeathDoNotNeedTicks() {
        val s = TimerEngine.start("t", "Outside", "Walk", start)
        val restored = Backups.json.decodeFromString<Session>(Backups.json.encodeToString(s))
        assertEquals(7_200_000L, TimerEngine.duration(restored, start.copy(elapsed = start.elapsed + 7_200_000)))
    }
    @Test fun wallClockChangesDoNotChangeElapsedTime() {
        val s = TimerEngine.start("t", "Outside", "Walk", start)
        val done = TimerEngine.finish(s, start.copy(wall = start.wall - 500_000, elapsed = 70_000))
        assertEquals(60_000L, TimerEngine.duration(done, start))
        assertTrue(done.notice.contains("clock changed"))
        assertEquals(start.wall + 60_000, done.spans.single().end)
    }
    @Test fun rebootConservativelyRetainsCheckpointAndRequiresReview() {
        val s = TimerEngine.checkpoint(TimerEngine.start("t", "Outside", "Walk", start), start.copy(elapsed = 40_000))
        val restored = TimerEngine.restore(s, start.copy(boot = 5, elapsed = 2000))
        assertEquals("interrupted", restored.state)
        assertEquals(30_000L, TimerEngine.duration(restored, start))
        assertTrue(restored.notice.isNotBlank())
        assertEquals(restored, TimerEngine.restore(restored, start.copy(boot = 5)))
    }
    @Test fun pausedSessionSurvivesRebootWithoutAddedTime() {
        val s = TimerEngine.pause(TimerEngine.start("t", "Outside", "", start), start.copy(elapsed = 20_000))
        assertEquals(s, TimerEngine.restore(s, start.copy(boot = 5, elapsed = 2)))
    }
    @Test fun resumingAfterBackwardClockChangeRetainsAllActiveTime() {
        val paused = TimerEngine.pause(TimerEngine.start("t", "Outside", "", start), start.copy(elapsed = 20_000))
        val resumed = TimerEngine.resume(paused, start.copy(wall = start.wall - 3_600_000, elapsed = 30_000))
        val finished = TimerEngine.finish(resumed, start.copy(wall = start.wall - 3_590_000, elapsed = 40_000))
        assertEquals(20_000L, TimerEngine.duration(finished, start))
        assertTrue(finished.notice.isNotBlank())
    }
    @Test fun duplicatePauseAndFinishAreIdempotent() {
        val s = TimerEngine.pause(TimerEngine.start("t", "Outside", "", start), start.copy(elapsed = 20_000))
        assertEquals(s, TimerEngine.pause(s, start.copy(elapsed = 90_000)))
        val done = TimerEngine.finish(s, start)
        assertEquals(done, TimerEngine.finish(done, start))
    }
}
