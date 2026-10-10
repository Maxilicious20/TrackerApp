@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.outsidedays.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.outsidedays.AppModel
import com.outsidedays.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable fun OutsideDays(model: AppModel) {
    val p by model.preferences.collectAsStateWithLifecycle()
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val ready by model.ready.collectAsStateWithLifecycle()
    val error by model.message.collectAsStateWithLifecycle()
    val imported by model.pendingImport.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    var editor by remember { mutableStateOf<Session?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if(event == Lifecycle.Event.ON_STOP) model.action("checkpoint") }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                model.repo.recover()
                while(true) { delay(30_000); model.repo.action("checkpoint") }
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; model.message.value = "Could not save timer state: ${e.message}" }
        }
    }
    LaunchedEffect(error) { error?.let { snack.showSnackbar(it); model.message.value = null } }
    val tabs = listOf("Today", "Journal", "Play", "Indoors", "Settings")
    val icons = listOf(Icons.Outlined.WbSunny, Icons.Outlined.DateRange, Icons.Outlined.SportsEsports, Icons.Outlined.Home, Icons.Outlined.Settings)
    Scaffold(snackbarHost = { SnackbarHost(snack) }, bottomBar = {
        NavigationBar {
            tabs.forEachIndexed { index, label -> NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icons[index], label) }, label = { Text(label, maxLines = 1) }) }
        }
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            if(!ready) Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("Preparing your little corner…"); Text("If local storage is unavailable, check the message below and try again."); OutlinedButton(onClick = { model.initialize() }) { Text("Retry loading") } }
            else AnimatedContent(targetState = tab, transitionSpec = { fadeIn(tween(if(p.reducedMotion) 0 else 180)) togetherWith fadeOut(tween(if(p.reducedMotion) 0 else 100)) }, label = "Screen") { selected ->
                Column(Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when(selected) {
                        0 -> HomeScreen(model, sessions, p) { editor = it }
                        1 -> JournalScreen(model, sessions, p) { editor = it }
                        2 -> GamesScreen(model, p)
                        3 -> IndoorScreen(model, sessions, p) { editor = it }
                        else -> SettingsScreen(model, p)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
    if (ready && !p.introSeen) AlertDialog(onDismissRequest = {}, title = { Text("A little more sky.") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { Meadow(true); Text("Welcome to Outside Days. Collect gentle moments outdoors with Mori, your little leaf spirit. No GPS, accounts, ads, or internet."); Text("Your memories stay on this device. Export backups to keep them safe before uninstalling. Play games only while safely seated or stationary.") }
    }, confirmButton = { Button(onClick = { model.prefs { it.copy(introSeen = true) } }) { Text("Make myself at home") } })
    editor?.let { SessionEditor(it, p.zone, onDismiss = { editor = null }, onSave = { s -> model.run { model.repo.saveSession(s); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { editor = null } } }) }
    imported?.let { b -> AlertDialog(onDismissRequest = { model.pendingImport.value = null }, title = { Text("Replace local data?") }, text = { Text("This verified backup contains ${b.sessions.size} sessions and ${b.games.size} games. Importing replaces all sessions, game progress, and preferences. Export your current data first if you want to keep it. Finish any active timer before importing.") }, dismissButton = { TextButton(onClick = { model.pendingImport.value = null }) { Text("Cancel") } }, confirmButton = { Button(onClick = { model.confirmImport() }) { Text("Replace and import") } }) }
}
