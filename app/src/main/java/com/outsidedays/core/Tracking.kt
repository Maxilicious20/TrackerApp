package com.outsidedays.core

import kotlinx.serialization.Serializable
import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlin.math.max
import kotlin.math.min

@Serializable data class Span(val start: Long, val end: Long)
@Serializable data class Session(
    val id: String,
    val activity: String = "Outside",
    val category: String = "Walk",
    val notes: String = "",
    val mood: String = "",
    val spans: List<Span> = emptyList(),
    val state: String = "complete",
    val anchorWall: Long = 0,
    val anchorElapsed: Long = 0,
    val boot: Int = 0,
    val checkpoint: Long = 0,
    val notice: String = "",
    val created: Long = 0
)
data class ClockSample(val wall: Long, val elapsed: Long, val boot: Int)

object TimerEngine {
    fun start(id: String, activity: String, category: String, now: ClockSample) = Session(
        id = id, activity = activity, category = category, state = "running",
        anchorWall = now.wall, anchorElapsed = now.elapsed, boot = now.boot, created = now.wall
    )
    fun interrupted(s: Session, now: ClockSample) = s.state == "running" && (s.boot != now.boot || now.elapsed < s.anchorElapsed)
    fun runningDuration(s: Session, now: ClockSample): Long = when {
        s.state != "running" -> 0
        interrupted(s, now) -> s.checkpoint
        else -> (now.elapsed - s.anchorElapsed).coerceAtLeast(0)
    }
    fun spans(s: Session, now: ClockSample): List<Span> = s.spans +
        if (s.state == "running") listOf(Span(s.anchorWall, s.anchorWall + runningDuration(s, now))) else emptyList()
    fun duration(s: Session, now: ClockSample) = union(spans(s, now)).sumOf { it.end - it.start }
    fun checkpoint(s: Session, now: ClockSample) = if (s.state == "running" && !interrupted(s, now)) s.copy(checkpoint = runningDuration(s, now)) else s
    fun restore(s: Session, now: ClockSample): Session = if (interrupted(s, now)) s.copy(
        spans = spans(s, now).filter { it.end > it.start }, state = "interrupted", checkpoint = 0,
        notice = "Your device restarted. Only time saved before the restart was kept. Resume or finish, then edit the session or add missing time manually."
    ) else s
    fun pause(s: Session, now: ClockSample): Session {
        val restored = restore(s, now)
        if (restored.state != "running") return restored
        val drift = kotlin.math.abs(now.wall - (s.anchorWall + runningDuration(s, now))) > 60_000
        return restored.copy(spans = spans(restored, now).filter { it.end > it.start }, state = "paused", checkpoint = 0,
            notice = if (drift) "The device clock changed. This interval uses its original start and monotonic elapsed time. Check its dates in History." else s.notice)
    }
    fun resume(s: Session, now: ClockSample): Session {
        require(s.state == "paused" || s.state == "interrupted")
        val lastEnd = s.spans.maxOfOrNull { it.end } ?: 0
        return s.copy(state = "running", anchorWall = maxOf(now.wall, lastEnd), anchorElapsed = now.elapsed, boot = now.boot, checkpoint = 0,
            notice = if (now.wall < lastEnd) "The clock moved backward. This resumed interval follows the previous interval to preserve elapsed time. Review its dates in Journal." else s.notice)
    }
    fun finish(s: Session, now: ClockSample) = pause(s, now).copy(state = "complete")
}

fun union(spans: List<Span>): List<Span> {
    val sorted = spans.filter { it.end > it.start }.sortedBy { it.start }
    val result = mutableListOf<Span>()
    for (s in sorted) {
        val last = result.lastOrNull()
        if (last != null && s.start <= last.end) result[result.lastIndex] = Span(last.start, max(last.end, s.end))
        else result += s
    }
    return result
}
object Reports {
    fun dayTotals(spans: List<Span>, zone: ZoneId): Map<LocalDate, Long> {
        val totals = sortedMapOf<LocalDate, Long>()
        for (span in union(spans)) {
            var cursor = span.start
            while (cursor < span.end) {
                val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val next = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = min(next, span.end)
                totals[day] = (totals[day] ?: 0) + end - cursor
                cursor = end
            }
        }
        return totals
    }
    fun between(spans: List<Span>, first: LocalDate, endExclusive: LocalDate, zone: ZoneId): Long {
        val a = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val b = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        return union(spans).sumOf { (min(b, it.end) - max(a, it.start)).coerceAtLeast(0) }
    }
    fun weekStart(day: LocalDate, firstDay: Int) = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(firstDay)))
    fun streak(totals: Map<LocalDate, Long>, today: LocalDate): Int {
        var day = if ((totals[today] ?: 0) > 0) today else today.minusDays(1)
        var count = 0
        while ((totals[day] ?: 0) > 0) { count++; day = day.minusDays(1) }
        return count
    }
}
fun durationText(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) / 1000)
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}
fun shortTime(ms: Long): String = if (ms < 60_000) "${ms / 1000}s" else if (ms < 3_600_000) "${ms / 60_000} min" else "${ms / 3_600_000}h ${ms / 60_000 % 60}m"
