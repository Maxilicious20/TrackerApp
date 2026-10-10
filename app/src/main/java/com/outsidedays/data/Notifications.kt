package com.outsidedays.data

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.outsidedays.MainActivity
import com.outsidedays.OutsideApp
import com.outsidedays.R
import com.outsidedays.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object Notifications {
    fun allowed(c: Context) = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun manager(c: Context) = c.getSystemService(NotificationManager::class.java)
    fun channels(c: Context) {
        manager(c).createNotificationChannel(NotificationChannel("timer", "Activity timer", NotificationManager.IMPORTANCE_LOW))
        manager(c).createNotificationChannel(NotificationChannel("reminder", "Gentle reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }
    private fun open(c: Context) = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun action(c: Context, name: String) = PendingIntent.getBroadcast(c, name.hashCode(), Intent(c, TimerActionReceiver::class.java).setAction(name), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun timer(c: Context, s: Session?, enabled: Boolean, clock: ClockSample) {
        if (s == null || !enabled || !allowed(c)) { manager(c).cancel(1); return }
        channels(c)
        val builder = NotificationCompat.Builder(c, "timer").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${s.activity} · ${s.state}").setContentText(if(s.state == "interrupted") "Open Outside Days to review interrupted time" else "A little time for yourself")
            .setContentIntent(open(c)).setOnlyAlertOnce(true).setOngoing(true)
        if (s.state == "running") builder.setUsesChronometer(true).setWhen(clock.wall - TimerEngine.duration(s, clock))
        else builder.setContentText(durationText(TimerEngine.duration(s, clock)))
        if (s.state == "running") builder.addAction(0, "Pause", action(c, "pause"))
        else if (s.state == "paused") builder.addAction(0, "Resume", action(c, "resume"))
        if (s.state != "interrupted") builder.addAction(0, "Finish", action(c, "finish"))
        manager(c).notify(1, builder.build())
    }
    fun reminder(c: Context) {
        if (!allowed(c)) return
        channels(c)
        manager(c).notify(2, NotificationCompat.Builder(c, "reminder").setSmallIcon(R.drawable.ic_notification).setContentTitle("A little sky time?")
            .setContentText("If it feels right, step outside. Every little moment counts.").setContentIntent(open(c)).setAutoCancel(true).build())
    }
    fun schedule(c: Context, p: Preferences, append: Boolean = false) {
        val work = WorkManager.getInstance(c)
        if (!p.reminders) { work.cancelUniqueWork("outside-reminder"); manager(c).cancel(2); return }
        val now = ZonedDateTime.now(); var next = now.withHour(p.reminderHour).withMinute(0).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(java.time.Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).build()
        work.enqueueUniqueWork("outside-reminder", if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
    }
}
class TimerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        (context.applicationContext as OutsideApp).scope.launch {
            try {
                val repo = (context.applicationContext as OutsideApp).repository
                repo.action(intent.action ?: "")
                Notifications.timer(context, repo.sessions.first().firstOrNull { it.state != "complete" }, repo.preferences.first().timerNotification, repo.clock())
            } catch (_: Exception) {
                // State changes are transactional; opening the app presents the persisted state.
            } finally { pending.finish() }
        }
    }
}
class ReminderWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result = try {
        val repo = (applicationContext as OutsideApp).repository
        val preferences = repo.preferences.first()
        if (preferences.reminders) { Notifications.reminder(applicationContext); Notifications.schedule(applicationContext, preferences, append = true) }
        Result.success()
    } catch (_: Exception) { Result.retry() }
}
