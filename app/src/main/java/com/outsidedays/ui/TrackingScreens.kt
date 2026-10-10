@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.outsidedays.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.outsidedays.AppModel
import com.outsidedays.core.*
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID

val categories = listOf("Walk", "Park", "Exercise", "Social time", "Errands", "Custom")
@Composable fun clockNow(model: AppModel): ClockSample {
    var now by remember { mutableStateOf(model.repo.clock()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while(true) { now = model.repo.clock(); delay(1000) } } }
    return now
}
@Composable fun ActiveTimer(model: AppModel, s: Session, p: Preferences, onFinish: (Session) -> Unit) {
    val now = clockNow(model)
    CozyCard {
        Text("${s.activity.uppercase()} · ${s.state.uppercase()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(durationText(TimerEngine.duration(s, now)), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { contentDescription = "Elapsed ${durationText(TimerEngine.duration(s, now))}, ${s.state}" })
        Text(s.category.ifBlank { "Time for yourself" })
        if(s.notice.isNotBlank()) Text(s.notice, color = MaterialTheme.colorScheme.error)
        if (s.state == "interrupted") Text("Time since the last saved checkpoint is uncertain. Continuing acknowledges this; you can correct missing intervals in Journal.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { model.action(if(s.state == "running") "pause" else "resume") }) { Text(if(s.state == "running") "Pause" else "Resume") }
            Button(onClick = { model.action("finish", onFinish) }) { Text(if(s.activity == "Outside") "I’m back home" else "Finish activity") }
        }
        if(s.state == "paused") Text("Rest easy. Paused time doesn’t count.", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable fun HomeScreen(model: AppModel, sessions: List<Session>, p: Preferences, edit: (Session) -> Unit) {
    val active = sessions.firstOrNull { it.state != "complete" }
    val now = clockNow(model); val zone = ZoneId.of(p.zone); val today = Instant.ofEpochMilli(now.wall).atZone(zone).toLocalDate()
    val spans = sessions.filter { it.activity == "Outside" }.flatMap { TimerEngine.spans(it, now) }
    val totals = Reports.dayTotals(spans, zone); val total = totals[today] ?: 0
    var category by rememberSaveable { mutableStateOf("Walk") }; var custom by rememberSaveable { mutableStateOf("") }
    Title("Outside Days", today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")) + "  ·  your little patch of calm")
    Meadow(p.reducedMotion, active?.state != "paused")
    Text(if(active?.activity == "Outside") "The world can wait a little." else "Good things grow outside.", style = MaterialTheme.typography.headlineMedium)
    if(active != null) ActiveTimer(model, active, p, edit)
    else CozyCard {
        Text("Where shall we wander?", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { model.start("Outside", if(category == "Custom") custom.ifBlank { "Outside" } else category) }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(20.dp)) { Text("I’m going outside", fontSize = 18.sp) }
        Choices(categories, category) { category = it }
        if(category == "Custom") OutlinedTextField(custom, { custom = it.take(80) }, label = { Text("Your activity (optional)") }, modifier = Modifier.fillMaxWidth())
        Text("No route to follow. Just a little fresh air.", style = MaterialTheme.typography.bodySmall)
    }
    CozyCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val target = if(p.dailyGoal == 0) 0f else (total / (p.dailyGoal * 60_000f)).coerceIn(0f, 1f)
            val progress by animateFloatAsState(target, tween(if(p.reducedMotion) 0 else 700), label = "Daily goal")
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(72.dp), strokeWidth = 7.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant); Text("✿", fontSize = 28.sp) }
            Column { Text("${shortTime(total)} outside", style = MaterialTheme.typography.titleLarge); Text(if(p.dailyGoal == 0) "Today, at your own pace" else "A gentle wish: ${p.dailyGoal} min / day") }
        }
        if(p.dailyGoal > 0 && total >= p.dailyGoal * 60_000L) Text("✧ A little wish fulfilled. Mori is cheering for you!", color = MaterialTheme.colorScheme.primary)
        Text("${Reports.streak(totals, today)} day gentle streak · A quiet day never takes your memories away.")
        TextButton(onClick = { edit(manualSession(now.wall)) }) { Text("+ Add forgotten time") }
    }
}
fun manualSession(now: Long, activity: String = "Outside") = Session(id = UUID.randomUUID().toString(), activity = activity, category = if(activity == "Outside") "Walk" else activity, spans = listOf(Span(now - 1_800_000, now)), created = now)

@Composable fun JournalScreen(model: AppModel, sessions: List<Session>, p: Preferences, edit: (Session) -> Unit) {
    val now = clockNow(model); val zone = ZoneId.of(p.zone); val today = Instant.ofEpochMilli(now.wall).atZone(zone).toLocalDate()
    val outdoors = sessions.filter { it.activity == "Outside" }
    val spans = outdoors.flatMap { TimerEngine.spans(it, now) }; val daily = Reports.dayTotals(spans, zone)
    var period by rememberSaveable { mutableStateOf("Week") }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Session?>(null) }
    var historyLimit by rememberSaveable(selectedDay) { mutableIntStateOf(30) }
    val start = when(period) { "Today" -> today; "Month" -> today.withDayOfMonth(1); "Year" -> today.withDayOfYear(1); else -> Reports.weekStart(today, p.firstDay) }
    val end = when(period) { "Today" -> start.plusDays(1); "Month" -> start.plusMonths(1); "Year" -> start.plusYears(1); else -> start.plusWeeks(1) }
    val total = Reports.between(spans, start, end, zone)
    val relevant = outdoors.filter { Reports.between(TimerEngine.spans(it, now), start, end, zone) > 0 }
    Title("Your sky journal", "Little moments make a lovely life.")
    Text("Reporting timezone: ${p.zone}. Travel does not move your past days.", style = MaterialTheme.typography.bodySmall)
    Choices(listOf("Today", "Week", "Month", "Year"), period) { period = it }
    CozyCard {
        Text(shortTime(total), style = MaterialTheme.typography.headlineLarge)
        Text("Outside this ${period.lowercase()}")
        Text("${relevant.size} sessions · ${shortTime(total / (java.time.temporal.ChronoUnit.DAYS.between(start, minOf(today.plusDays(1), end))).coerceAtLeast(1))} daily average")
        Text("Longest in this period: ${shortTime(relevant.maxOfOrNull { Reports.between(TimerEngine.spans(it, now), start, end, zone) } ?: 0)}")
        val week = Reports.weekStart(today, p.firstDay)
        val weekTotal = Reports.between(spans, week, week.plusWeeks(1), zone)
        if(p.weeklyGoal > 0) { LinearProgressIndicator(progress = { (weekTotal / (p.weeklyGoal * 60_000f)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); Text("This week: ${shortTime(weekTotal)} of your ${p.weeklyGoal} min wish") }
    }
    CozyCard {
        Text("The last seven days", style = MaterialTheme.typography.titleLarge)
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }; val max = dates.maxOf { daily[it] ?: 0 }.coerceAtLeast(1)
        dates.forEach { d ->
            val value = daily[d] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.semantics { contentDescription = "$d, ${shortTime(value)} outside" }) {
                Text(d.dayOfWeek.name.take(3), modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { value.toFloat() / max }, modifier = Modifier.weight(1f).height(9.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                Text(shortTime(value), modifier = Modifier.width(68.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    CozyCard {
        val month = YearMonth.parse(monthText)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { monthText = month.minusMonths(1).toString() }) { Text("‹", modifier = Modifier.semantics { contentDescription = "Previous month" }) }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { monthText = month.plusMonths(1).toString() }) { Text("›", modifier = Modifier.semantics { contentDescription = "Next month" }) }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val calendarWidth = maxOf(maxWidth, 336.dp)
        Row(Modifier.horizontalScroll(rememberScrollState())) { Column(Modifier.width(calendarWidth)) {
        val offset = (month.atDay(1).dayOfWeek.value - p.firstDay + 7) % 7
        Row { repeat(7) { i -> Text(DayOfWeek.of((p.firstDay - 1 + i) % 7 + 1).name.take(1), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
        repeat((offset + month.lengthOfMonth() + 6) / 7) { row ->
            Row {
                repeat(7) { col ->
                    val day = row * 7 + col - offset + 1
                    if(day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                    else {
                        val date = month.atDay(day); val value = daily[date] ?: 0
                        Column(Modifier.weight(1f).heightIn(min = 48.dp).background(if(selectedDay == date.toString()) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp)).clickable { selectedDay = if(selectedDay == date.toString()) null else date.toString() }.semantics { contentDescription = "$date, ${shortTime(value)} outside. Select to filter sessions." }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(day.toString()); Text(if(value > 0) "•" else "", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        } } }
        selectedDay?.let { Text("$it · ${shortTime(daily[LocalDate.parse(it)] ?: 0)} outside"); TextButton(onClick = { selectedDay = null }) { Text("Show all sessions") } }
    }
    CozyCard {
        Text("Little treasures", style = MaterialTheme.typography.titleLarge)
        val lifetime = union(spans).sumOf { it.end - it.start }; val days = daily.count { it.value > 0 }
        Text(if(lifetime > 0) "✿ First leaf — your first outside moment" else "♡ First leaf — a first moment is waiting")
        Text(if(days >= 7) "✦ Sky collector — seven days with fresh air" else "♡ Sky collector — $days / 7 days, at your pace")
        Text(if(lifetime >= 10 * 3_600_000L) "☀ Sun keeper — ten hours outside" else "♡ Sun keeper — ${shortTime(lifetime)} / 10h")
        Text("${Reports.streak(daily, today)} day streak. No penalties, no lost treasures.")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Memories", style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = { edit(manualSession(now.wall)) }) { Text("+ Add") }
    }
    val history = sessions.filter { it.state == "complete" && (selectedDay == null || Reports.between(it.spans, LocalDate.parse(selectedDay), LocalDate.parse(selectedDay).plusDays(1), zone) > 0) }
    if(history.isEmpty()) CozyCard { Text("A blank page, a whole sky ahead.", style = MaterialTheme.typography.titleLarge); Text("Your completed moments will settle here. You can also add one you forgot to track.") }
    history.take(historyLimit).forEach { s ->
        CozyCard {
            Text("${s.category} · ${s.activity}", style = MaterialTheme.typography.titleLarge)
            Text("${Instant.ofEpochMilli(s.spans.firstOrNull()?.start ?: s.created).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm"))} · ${shortTime(union(s.spans).sumOf { it.end - it.start })}")
            if(s.mood.isNotBlank()) Text("Mood: ${s.mood}")
            if(s.notes.isNotBlank()) Text(s.notes)
            if(s.notice.isNotBlank()) Text(s.notice, style = MaterialTheme.typography.bodySmall)
            Row { TextButton(onClick = { edit(s) }) { Text("Edit") }; TextButton(onClick = { deleting = s }) { Text("Delete") } }
        }
    }
    if(history.size > historyLimit) OutlinedButton(onClick = { historyLimit += 30 }) { Text("Show more memories (${history.size - historyLimit} remaining)") }
    deleting?.let { s -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this memory?") }, text = { Text("This session will be removed from your journal and totals.") }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep it") } }, confirmButton = { TextButton(onClick = { model.run { model.repo.delete(s.id) }; deleting = null }) { Text("Delete session") } }) }
}

@Composable fun IndoorScreen(model: AppModel, sessions: List<Session>, p: Preferences, edit: (Session) -> Unit) {
    val now = clockNow(model); val zone = ZoneId.of(p.zone); val today = Instant.ofEpochMilli(now.wall).atZone(zone).toLocalDate()
    val active = sessions.firstOrNull { it.state != "complete" }
    var switching by rememberSaveable { mutableStateOf<String?>(null) }
    var breathing by rememberSaveable { mutableStateOf(false) }
    var second by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(breathing, lifecycle) { second = 0; lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while(breathing) { delay(1000); second = (second + 1) % 12 } } }
    val phase = if(second < 4) "Breathe in" else if(second < 6) "Rest gently" else "Breathe out"
    val breathSize by animateDpAsState(if(breathing && second < 6) 136.dp else 92.dp, tween(if(p.reducedMotion) 0 else if(second < 4) 3500 else 5500), label = "Breathing")
    Title("A quiet corner", "A small space for your indoor rituals.")
    Text("Indoor minutes stay separate from your outside days.")
    if(active != null) ActiveTimer(model, active, p, edit)
    listOf("Reading" to "Just one more chapter", "Focus" to "Studying or focused work", "Relaxing" to "Stretch, unwind, do a little less").forEach { (activity, detail) ->
        val total = Reports.between(sessions.filter { it.activity == activity }.flatMap { TimerEngine.spans(it, now) }, today, today.plusDays(1), zone)
        CozyCard {
            Text(activity, style = MaterialTheme.typography.titleLarge); Text(detail); Text("Today: ${shortTime(total)}")
            Button(onClick = { if(active == null) model.start(activity, activity) else switching = activity }, enabled = active?.activity != activity) { Text(if(active == null) "Start $activity" else if(active.activity == activity) "Current activity" else "Switch to $activity") }
        }
    }
    CozyCard {
        Text("A softer breath", style = MaterialTheme.typography.titleLarge)
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(breathSize).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Text(if(breathing) phase else "Take a breath", modifier = Modifier.padding(12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        Text("In for 4 · rest for 2 · out for 6. Follow your own comfort.")
        OutlinedButton(onClick = { breathing = !breathing }) { Text(if(breathing) "Stop breathing guide" else "Begin breathing guide") }
    }
    switching?.let { next -> AlertDialog(onDismissRequest = { switching = null }, title = { Text("Switch activities?") }, text = { Text("Finish ${active?.activity ?: "the current activity"} and start $next? Only one activity timer runs at a time. You can add notes to the finished session in Journal.") }, dismissButton = { TextButton(onClick = { switching = null }) { Text("Cancel") } }, confirmButton = { Button(onClick = { model.run { model.repo.action("finish"); model.repo.start(next, next) }; switching = null }) { Text("Finish and switch") } }) }
}
