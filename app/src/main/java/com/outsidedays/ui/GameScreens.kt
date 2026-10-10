@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.outsidedays.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.*
import com.outsidedays.AppModel
import com.outsidedays.core.*
import kotlinx.coroutines.delay

val nature = listOf("Leaf", "Flower", "Moon", "Star", "Cloud", "Acorn", "Sun", "Sprout")
val symbols = listOf("❧", "✿", "☾", "✧", "☁", "♧", "☀", "♣")
val instructions = mapOf(
    "Leaf Match" to "Turn over two cards and find matching nature friends. A mismatched pair stays visible until your next turn. Match every pair to complete the garden. Fewer turns earn a higher score.",
    "Cloud Slide" to "Tap a tile beside the empty space to slide it. Arrange the landscape from 1 to 8, left to right, with the empty sky in the bottom-right corner. Every puzzle starts from a solvable shuffle.",
    "Firefly Garden" to "Tap the glowing firefly to help it flutter to a new spot. Gather as many as you like in a 30-second round. No penalties for missed taps. Pausing or leaving freezes the round.",
    "Pocket Sudoku" to "Fill each row, column, and outlined box with 1–4 or 1–9 exactly once. Tap a cell, then a number. Notes toggle little candidate numbers. Conflicts show an exclamation mark. Hints fill one correct cell. Both puzzles have exactly one solution.",
    "Cozy Merge" to "Use the direction buttons to slide all tiles. Equal neighbors merge once per move; a new seed appears after each valid move. Grow a 2048 tile to win. The garden is full when no moves remain."
)
@Composable fun GamesScreen(model: AppModel, p: Preferences) {
    val games by model.games.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var paused by rememberSaveable { mutableStateOf(true) }
    var restart by rememberSaveable { mutableStateOf(false) }
    var difficulty by rememberSaveable { mutableIntStateOf(4) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_PAUSE) paused = true }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(selected != null) { selected = null; paused = true }
    val haptic = LocalHapticFeedback.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 18) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    fun feedback() {
        if(p.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if(p.sound) runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 45) }
    }
    val kind = selected
    if(kind == null) {
        Title("Pocket of play", "Small games for unhurried afternoons.")
        CozyCard { Text("Find a comfy place first.", style = MaterialTheme.typography.titleLarge); Text("These games are for when you’re safely seated or stationary. Your outside timer keeps going, independently.") }
        val descriptions = listOf("A leafy little memory garden", "Put a pastel sky back together", "A soft glow, a gentle tap", "A little room for a clear mind", "From tiny seeds to a flourishing grove")
        Games.names.forEachIndexed { i, name ->
            CozyCard {
                Text("${symbols[i]}  $name", style = MaterialTheme.typography.headlineMedium)
                Text(descriptions[i]); games[name]?.let { Text("Best score: ${it.best} · ${if(it.status == "playing") "Saved game waiting" else "Last game completed"}", style = MaterialTheme.typography.bodySmall) }
                Button(modifier = Modifier.testTag("open-$name"), onClick = { selected = name; paused = true; difficulty = games[name]?.size ?: 4; if(games[name] == null) model.game(name) { Games.new(name) } }) { Text(if(games[name] == null) "Open game" else "Continue") }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { selected = null; paused = true }) { Text("‹ All games") }
        TextButton(onClick = { model.prefs { it.copy(sound = !it.sound) } }) { Text(if(p.sound) "Mute sounds" else "Sound off") }
    }
    Text(kind, style = MaterialTheme.typography.headlineLarge)
    Text(instructions.getValue(kind))
    val game = games[kind]
    if(game == null) { CircularProgressIndicator(); return }
    if(kind == "Leaf Match" || kind == "Pocket Sudoku") {
        val labels = if(kind == "Leaf Match") listOf("Easy · 12 cards", "Dreamy · 16 cards") else listOf("Beginner · 4×4", "Classic · 9×9")
        Choices(labels, if((kind == "Leaf Match" && game.size == 3) || (kind == "Pocket Sudoku" && game.size == 4)) labels[0] else labels[1]) { value ->
            difficulty = if(kind == "Leaf Match") (if(value == labels[0]) 3 else 4) else (if(value == labels[0]) 4 else 9)
            paused = true; restart = true
        }
    }
    Text("${game.moves} moves · Score ${game.score} · Best ${game.best}", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { paused = !paused }, enabled = game.status == "playing") { Text(if(paused) "Play / resume" else "Pause game") }
        OutlinedButton(onClick = { difficulty = game.size; paused = true; restart = true }) { Text("New / restart") }
    }
    if(game.status != "playing") {
        val scale by animateFloatAsState(if(p.reducedMotion) 1f else 1.05f, tween(350), label = "Celebration")
        CozyCard { Text(if(game.status == "won") "✧ A lovely little victory!" else "Your garden is full.", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding((scale * 2).dp)); Text(if(kind == "Firefly Garden") "${game.score} fireflies found their way home." else if(game.status == "lost") "Every garden is a new beginning. Start again whenever you like." else "Take a breath. You made something beautiful.") }
    }
    if(paused && game.status == "playing") CozyCard { Meadow(p.reducedMotion, false); Text("Resting here. Your progress is saved.", style = MaterialTheme.typography.titleLarge); Text("Tap Play / resume when you’re settled. Leaving a game pauses it; the activity timer is independent.") }
    else {
        when(kind) {
            "Leaf Match" -> NatureGrid(game.board.size / game.size, game.size) { index ->
                val visible = index in game.opened || index in game.matched || game.status == "won"
                GameTile(if(visible) symbols[game.board[index]] else "❀", if(visible) nature[game.board[index]] else "Face-down card ${index + 1}", selected = index in game.matched, enabled = game.status == "playing", onClick = { feedback(); model.game(kind) { Games.leaf(it!!, index) } })
            }
            "Cloud Slide" -> NatureGrid(3, 3) { index ->
                val value = game.board[index]
                LandscapeTile(value, value != 0 && game.status == "playing") { feedback(); model.game(kind) { Games.slide(it!!, index) } }
            }
            "Firefly Garden" -> {
                Text("${(game.remaining + 999) / 1000}s left", style = MaterialTheme.typography.headlineMedium)
                LinearProgressIndicator(progress = { game.remaining / 30_000f }, modifier = Modifier.fillMaxWidth())
                NatureGrid(3, 3) { index -> GameTile(if(index == game.target && game.status == "playing") "✦" else "·", if(index == game.target) "Glowing firefly" else "Garden patch", selected = index == game.target, enabled = game.status == "playing", onClick = { if(index == game.target) feedback(); model.game(kind) { Games.firefly(it!!, index) } }) }
                LaunchedEffect(kind, paused, game.status) {
                    if(!paused && game.status == "playing") {
                        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                            var last = SystemClock.elapsedRealtime()
                            while(true) { delay(1000); val now = SystemClock.elapsedRealtime(); val delta = now - last; last = now; model.game(kind) { Games.tick(it!!, delta) } }
                        }
                    }
                }
            }
            "Pocket Sudoku" -> SudokuBoard(game, { cell, digit, note -> feedback(); model.game(kind) { Games.sudoku(it!!, cell, digit, note) } }, { cell -> model.game(kind) { Games.hint(it!!, cell) } })
            "Cozy Merge" -> {
                NatureGrid(4, 4) { index ->
                    val value = game.board[index]
                    GameTile(if(value == 0) "·" else "$value\n${if(value < 16) "♣" else if(value < 128) "❧" else "✿"}", if(value == 0) "Empty plot" else "Nature tile $value", selected = value >= 32, enabled = false) {}
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    fun move(direction: Int) { feedback(); model.game(kind) { Games.merge(it!!, direction) } }
                    Button(onClick = { move(2) }, enabled = game.status == "playing") { Text("↑ Up") }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { move(0) }, enabled = game.status == "playing") { Text("← Left") }; Button(onClick = { move(1) }, enabled = game.status == "playing") { Text("Right →") } }
                    Button(onClick = { move(3) }, enabled = game.status == "playing") { Text("↓ Down") }
                }
            }
        }
    }
    if(restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text("Plant a new game?") }, text = { Text("This replaces the current board. Your best score is kept. Sudoku has one verified puzzle per size; restarting resets that puzzle.") }, dismissButton = { TextButton(onClick = { restart = false }) { Text("Cancel") } }, confirmButton = { Button(onClick = { model.game(kind) { Games.new(kind, difficulty, it?.best ?: 0) }; restart = false; paused = true }) { Text("New game") } })
}
@Composable fun NatureGrid(rows: Int, columns: Int, cell: @Composable (Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(columns) { col -> Box(Modifier.weight(1f).aspectRatio(1f)) { cell(row * columns + col) } } } }
    }
}
@Composable fun GameTile(text: String, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val background by animateColorAsState(if(selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, tween(if(reduced) 0 else 180), label = "Tile response")
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize().semantics { contentDescription = label }, shape = RoundedCornerShape(18.dp),
        color = background, contentColor = if(selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(text, transitionSpec = { fadeIn(tween(if(reduced) 0 else 140)) togetherWith fadeOut(tween(if(reduced) 0 else 100)) }, label = "Tile reveal") { value ->
                Text(value, fontSize = 25.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Medium)
            }
        }
    }
}
@Composable fun SudokuBoard(game: Game, place: (Int, Int, Boolean) -> Unit, hint: (Int) -> Unit) {
    var selected by rememberSaveable(game.size) { mutableIntStateOf(-1) }
    var notes by rememberSaveable { mutableStateOf(false) }
    val conflicts = remember(game.board) { Sudoku.conflicts(game.board, game.size) }
    Text("${game.hints} hints used · ${conflicts.size} conflicting cells")
    if(game.size == 9) Text("Swipe the board sideways on a small screen. Every cell keeps a 48 dp touch target.", style = MaterialTheme.typography.bodySmall)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxOf(maxWidth, (game.size * 48).dp)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(width).border(2.dp, MaterialTheme.colorScheme.primary)) {
                repeat(game.size) { row ->
                    Row {
                        repeat(game.size) { col ->
                            val i = row * game.size + col; val value = game.board[i]; val fixed = game.givens[i] != 0; val box = if(game.size == 4) 2 else 3
                            val background = if(selected == i) MaterialTheme.colorScheme.primaryContainer else if((row / box + col / box) % 2 == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                            Surface(onClick = { selected = i }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).aspectRatio(1f).border(if(col % box == 0 || row % box == 0) 1.dp else .5.dp, MaterialTheme.colorScheme.outline).semantics { contentDescription = "Row ${row+1}, column ${col+1}, ${if(value == 0) "empty" else value}${if(fixed) ", fixed" else ""}${if(i in conflicts) ", conflict" else ""}" }, color = background) {
                                Box(contentAlignment = Alignment.Center) {
                                    if(value != 0) Text("$value${if(i in conflicts) "!" else ""}", fontSize = 21.sp, fontWeight = if(fixed) FontWeight.Bold else FontWeight.Normal, color = if(i in conflicts) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                                    else Text((1..game.size).filter { game.notes[i] and (1 shl it) != 0 }.joinToString(" "), fontSize = 10.sp, modifier = Modifier.padding(3.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..game.size).forEach { value -> FilledTonalButton(onClick = { place(selected, value, notes) }, enabled = selected >= 0 && game.givens.getOrNull(selected) == 0 && game.status == "playing", contentPadding = PaddingValues(10.dp), modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)) { Text(value.toString()) } }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = notes, onClick = { notes = !notes }, label = { Text("Notes ${if(notes) "on" else "off"}") })
        TextButton(onClick = { place(selected, 0, false) }, enabled = selected >= 0 && game.status == "playing") { Text("Erase") }
        TextButton(onClick = { hint(selected) }, enabled = game.status == "playing") { Text("Gentle hint") }
    }
}
