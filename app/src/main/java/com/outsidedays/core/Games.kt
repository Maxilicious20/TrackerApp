package com.outsidedays.core

import kotlinx.serialization.Serializable
import kotlin.random.Random
import kotlin.math.abs

@Serializable data class Game(
    val kind: String, val size: Int = 4, val board: List<Int> = emptyList(),
    val solution: List<Int> = emptyList(), val givens: List<Int> = emptyList(),
    val notes: List<Int> = emptyList(), val opened: List<Int> = emptyList(),
    val matched: List<Int> = emptyList(), val moves: Int = 0, val score: Int = 0,
    val best: Int = 0, val status: String = "playing", val remaining: Long = 30_000,
    val target: Int = 0, val hints: Int = 0
)
object Games {
    val names = listOf("Leaf Match", "Cloud Slide", "Firefly Garden", "Pocket Sudoku", "Cozy Merge")
    fun new(kind: String, size: Int = 4, best: Int = 0, random: Random = Random.Default): Game = when(kind) {
        "Leaf Match" -> Game(kind, size = size, board = (0 until (if (size == 4) 8 else 6)).flatMap { listOf(it, it) }.shuffled(random), best = best)
        "Cloud Slide" -> {
            var b = (1..8).toList() + 0
            var previous = -1
            repeat(100) {
                val zero = b.indexOf(0)
                val adjacent = b.indices.filter { it != previous && adjacent(zero, it, 3) }
                val next = adjacent.random(random)
                b = swap(b, zero, next); previous = zero
            }
            if (b == (1..8).toList() + 0) b = swap(b, 8, 7)
            Game(kind, size = 3, board = b, best = best)
        }
        "Pocket Sudoku" -> {
            val puzzle = if (size == 4) "1204041001404001" else "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
            val b = puzzle.map { it.digitToInt() }
            val sol = Sudoku.solutions(b, size, 2)
            require(sol.size == 1) { "Puzzle must have exactly one solution" }
            Game(kind, size = size, board = b, givens = b, solution = sol.single(), notes = List(b.size) { 0 }, best = best)
        }
        "Cozy Merge" -> Game(kind, board = spawn(spawn(List(16) { 0 }, random), random), best = best)
        else -> Game("Firefly Garden", size = 3, best = best, target = random.nextInt(9))
    }
    fun leaf(g: Game, index: Int): Game {
        if (g.status != "playing" || index in g.matched || index !in g.board.indices) return g
        val opened = if (g.opened.size == 2) emptyList() else g.opened
        if (index in opened) return g
        val next = opened + index
        if (next.size != 2) return g.copy(opened = next)
        val matched = if (g.board[next[0]] == g.board[next[1]]) g.matched + next else g.matched
        val won = matched.size == g.board.size
        val score = if (won) (1000 - (g.moves + 1) * 10).coerceAtLeast(10) else g.score
        return g.copy(opened = next, matched = matched, moves = g.moves + 1, score = score, best = maxOf(g.best, score), status = if (won) "won" else "playing")
    }
    fun adjacent(a: Int, b: Int, n: Int) = abs(a / n - b / n) + abs(a % n - b % n) == 1
    fun swap(b: List<Int>, a: Int, c: Int) = b.toMutableList().apply { val t = this[a]; this[a] = this[c]; this[c] = t }.toList()
    fun slide(g: Game, index: Int): Game {
        if (g.status != "playing" || !adjacent(g.board.indexOf(0), index, 3)) return g
        val b = swap(g.board, g.board.indexOf(0), index)
        val won = b == (1..8).toList() + 0
        val score = if (won) (1000 - (g.moves + 1)).coerceAtLeast(1) else 0
        return g.copy(board = b, moves = g.moves + 1, status = if (won) "won" else "playing", score = score, best = maxOf(g.best, score))
    }
    fun solvable(board: List<Int>): Boolean = board.indices.sumOf { i -> (i + 1 until board.size).count { j -> board[i] > 0 && board[j] > 0 && board[i] > board[j] } } % 2 == 0
    fun firefly(g: Game, index: Int, random: Random = Random.Default): Game {
        if (g.status != "playing" || index != g.target) return g
        val next = (0..8).filter { it != index }.random(random)
        return g.copy(score = g.score + 1, best = maxOf(g.best, g.score + 1), target = next)
    }
    fun tick(g: Game, elapsed: Long): Game {
        if (g.kind != "Firefly Garden" || g.status != "playing") return g
        val left = (g.remaining - elapsed.coerceAtLeast(0)).coerceAtLeast(0)
        return g.copy(remaining = left, status = if (left == 0L) "won" else "playing")
    }
    fun sudoku(g: Game, cell: Int, digit: Int, note: Boolean): Game {
        if (g.status != "playing" || cell !in g.board.indices || g.givens[cell] != 0 || digit !in 0..g.size) return g
        if (note && digit > 0) return g.copy(notes = g.notes.toMutableList().apply { this[cell] = this[cell] xor (1 shl digit) })
        val b = g.board.toMutableList().apply { this[cell] = digit }.toList()
        val won = b == g.solution
        return g.copy(board = b, notes = g.notes.toMutableList().apply { this[cell] = 0 }, moves = g.moves + 1,
            status = if (won) "won" else "playing", score = if (won) 100 else 0, best = if (won) maxOf(100, g.best) else g.best)
    }
    fun hint(g: Game, selected: Int): Game {
        val i = if (selected in g.board.indices && g.givens[selected] == 0 && g.board[selected] != g.solution[selected]) selected else g.board.indices.firstOrNull { g.board[it] != g.solution[it] } ?: return g
        return sudoku(g, i, g.solution[i], false).copy(hints = g.hints + 1)
    }
    fun collapse(line: List<Int>): Pair<List<Int>, Int> {
        val values = line.filter { it != 0 }; val out = mutableListOf<Int>(); var score = 0; var i = 0
        while (i < values.size) {
            if (i + 1 < values.size && values[i] == values[i + 1]) { val v = values[i] * 2; out += v; score += v; i += 2 }
            else { out += values[i]; i++ }
        }
        return (out + List(line.size - out.size) { 0 }) to score
    }
    fun spawn(board: List<Int>, random: Random = Random.Default): List<Int> {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return board
        return board.toMutableList().apply { this[empty.random(random)] = if (random.nextInt(10) == 0) 4 else 2 }
    }
    fun merge(g: Game, direction: Int, random: Random = Random.Default): Game {
        if (g.status != "playing") return g
        val b = g.board.toMutableList(); var gain = 0
        for (row in 0..3) {
            val indices = (0..3).map { col -> when(direction) { 0 -> row * 4 + col; 1 -> row * 4 + (3 - col); 2 -> col * 4 + row; else -> (3 - col) * 4 + row } }
            val (values, points) = collapse(indices.map { g.board[it] }); gain += points
            indices.forEachIndexed { i, index -> b[index] = values[i] }
        }
        if (b == g.board) return g
        val board = spawn(b, random); val score = g.score + gain
        return g.copy(board = board, score = score, best = maxOf(g.best, score), moves = g.moves + 1,
            status = if (board.any { it >= 2048 }) "won" else if (!canMove(board)) "lost" else "playing")
    }
    fun canMove(b: List<Int>) = b.any { it == 0 } || b.indices.any { i -> (i % 4 < 3 && b[i] == b[i + 1]) || (i < 12 && b[i] == b[i + 4]) }
}
object Sudoku {
    fun allowed(b: List<Int>, n: Int, i: Int, value: Int): Boolean {
        val box = if (n == 4) 2 else 3
        return b.indices.none { j -> j != i && b[j] == value && (j / n == i / n || j % n == i % n || (j / n / box == i / n / box && j % n / box == i % n / box)) }
    }
    fun conflicts(b: List<Int>, n: Int): Set<Int> = b.indices.filter { b[it] != 0 && !allowed(b, n, it, b[it]) }.toSet()
    fun solutions(board: List<Int>, n: Int, limit: Int = 2): List<List<Int>> {
        require(n == 4 || n == 9); require(board.size == n * n)
        if (board.any { it !in 0..n } || conflicts(board, n).isNotEmpty()) return emptyList()
        val result = mutableListOf<List<Int>>()
        var visits = 0
        fun solve(b: List<Int>) {
            if (result.size >= limit) return
            require(++visits <= 200_000) { "Puzzle verification exceeded its safe search limit" }
            val i = b.indexOf(0)
            if (i == -1) { result += b; return }
            for (v in 1..n) if (allowed(b, n, i, v)) solve(b.toMutableList().apply { this[i] = v })
        }
        solve(board); return result
    }
}
