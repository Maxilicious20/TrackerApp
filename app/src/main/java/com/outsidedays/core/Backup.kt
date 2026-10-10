package com.outsidedays.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId

@Serializable data class Preferences(
    val theme: String = "System", val accent: String = "Sage", val reducedMotion: Boolean = false,
    val haptics: Boolean = true, val sound: Boolean = false, val dailyGoal: Int = 30,
    val weeklyGoal: Int = 150, val firstDay: Int = 1, val zone: String = ZoneId.systemDefault().id,
    val introSeen: Boolean = false, val reminders: Boolean = false, val reminderHour: Int = 17,
    val timerNotification: Boolean = false
)
@Serializable data class Backup(val version: Int = 1, val sessions: List<Session>, val games: List<Game>, val preferences: Preferences)
object Backups {
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    const val MAX_BYTES = 5 * 1024 * 1024
    fun encode(backup: Backup): String { validate(backup); val text = json.encodeToString(backup); require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup exceeds the 5 MB import limit. Export CSV for an archive." }; return text }
    fun decode(text: String): Backup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Backup is larger than 5 MB" }
        return json.decodeFromString<Backup>(text).also(::validate)
    }
    fun validate(b: Backup) {
        require(b.version == 1) { "Unsupported backup version" }
        require(b.sessions.size <= 10000 && b.games.size <= 5) { "Too many records" }
        require(b.sessions.map { it.id }.distinct().size == b.sessions.size) { "Duplicate session IDs" }
        require(b.games.map { it.kind }.distinct().size == b.games.size) { "Duplicate games" }
        validatePreferences(b.preferences)
        b.sessions.forEach { s ->
            require(s.id.length in 1..100 && s.state == "complete") { "Only completed sessions can be imported" }
            require(s.activity in listOf("Outside", "Reading", "Focus", "Relaxing")) { "Unknown activity" }
            require(s.notes.length <= 4000 && s.category.length <= 80 && s.mood.length <= 40 && s.notice.length <= 1000)
            require(s.spans.size <= 10000)
            s.spans.forEach { require(it.start >= 0 && it.end >= it.start && it.end <= 4_102_444_800_000L && it.end - it.start <= 366L * 86_400_000) { "Invalid session interval" } }
        }
        b.games.forEach(::validateGame)
    }
    fun validatePreferences(p: Preferences) {
        require(p.theme in listOf("System", "Light", "Dark") && p.accent in listOf("Sage", "Sky", "Lavender", "Peach"))
        require(p.dailyGoal in 0..1440 && p.weeklyGoal in 0..10080 && p.firstDay in 1..7 && p.reminderHour in 0..23)
        ZoneId.of(p.zone)
    }
    fun validateGame(g: Game) {
        require(g.kind in Games.names && g.status in listOf("playing", "won", "lost")) { "Unknown game" }
        require(g.moves in 0..10_000_000 && g.score in 0..100_000_000 && g.best in 0..100_000_000 && g.hints in 0..100_000)
        when (g.kind) {
            "Leaf Match" -> {
                require(g.size in listOf(3, 4)); require(g.board.size == if (g.size == 4) 16 else 12)
                require(g.board.groupingBy { it }.eachCount().let { it.size == g.board.size / 2 && it.all { (k, v) -> k in 0..7 && v == 2 } })
                require(g.opened.size <= 2 && g.opened.distinct().size == g.opened.size)
                require((g.opened + g.matched).all { it in g.board.indices } && g.matched.distinct().size == g.matched.size)
                require(g.matched.map { g.board[it] }.groupingBy { it }.eachCount().values.all { it == 2 })
            }
            "Cloud Slide" -> { require(g.size == 3 && g.board.sorted() == (0..8).toList() && Games.solvable(g.board)) }
            "Firefly Garden" -> { require(g.size == 3 && g.remaining in 0..30_000 && g.target in 0..8) }
            "Pocket Sudoku" -> {
                require(g.size in listOf(4, 9)); val length = g.size * g.size
                require(listOf(g.board, g.givens, g.solution, g.notes).all { it.size == length })
                require((g.board + g.givens + g.solution).all { it in 0..g.size })
                require(g.solution.none { it == 0 } && Sudoku.conflicts(g.solution, g.size).isEmpty())
                require(g.givens.indices.all { g.givens[it] == 0 || (g.givens[it] == g.solution[it] && g.board[it] == g.givens[it]) })
                require(g.notes.all { it >= 0 && it < (1 shl (g.size + 1)) })
                // Limit search complexity before accepting untrusted puzzle inputs.
                require(g.givens.count { it != 0 } >= if (g.size == 4) 6 else 24)
                val solutions = Sudoku.solutions(g.givens, g.size, 2)
                require(solutions.size == 1 && solutions.single() == g.solution) { "Sudoku must have a unique solution" }
            }
            "Cozy Merge" -> require(g.size == 4 && g.board.size == 16 && g.board.all { it == 0 || (it in 2..2048 && (it and (it - 1)) == 0) })
        }
    }
    fun csv(sessions: List<Session>): String {
        fun q(s: String) = "\"" + (if (s.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'" else "") + s.replace("\"", "\"\"") + "\""
        return "id,activity,category,start_utc,end_utc,active_seconds,mood,notes\r\n" + sessions.filter { it.state == "complete" }.joinToString("\r\n") { s ->
            val spans = union(s.spans)
            listOf(s.id, s.activity, s.category, spans.firstOrNull()?.let { java.time.Instant.ofEpochMilli(it.start).toString() } ?: "", spans.lastOrNull()?.let { java.time.Instant.ofEpochMilli(it.end).toString() } ?: "", (spans.sumOf { it.end - it.start } / 1000).toString(), s.mood, s.notes).joinToString(",", transform = ::q)
        }
    }
}
