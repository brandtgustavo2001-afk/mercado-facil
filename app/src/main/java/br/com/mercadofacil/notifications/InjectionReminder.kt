package br.com.mercadofacil.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.mercadofacil.MainActivity
import br.com.mercadofacil.MercadoApp
import br.com.mercadofacil.database.InjectionSchedule
import java.util.concurrent.TimeUnit

object InjectionReminderScheduler {
    private fun workName(id: String) = "injection-reminder-$id"

    fun schedule(context: android.content.Context, schedule: InjectionSchedule) {
        if (!schedule.active || !schedule.reminderEnabled) {
            cancel(context, schedule.id)
            return
        }
        val request = OneTimeWorkRequestBuilder<InjectionReminderWorker>()
            .setInputData(workDataOf("schedule_id" to schedule.id, "due_at" to schedule.nextDueAt))
            .setInitialDelay((schedule.nextDueAt - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(schedule.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: android.content.Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }
}

class InjectionReminderWorker(context: android.content.Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MercadoApp
        val scheduleId = inputData.getString("schedule_id") ?: return Result.success()
        val dueAt = inputData.getLong("due_at", -1L)
        val schedule = app.repository.dao.injectionSchedule(scheduleId) ?: return Result.success()
        if (!schedule.active || !schedule.reminderEnabled || schedule.nextDueAt != dueAt) return Result.success()
        if (!app.settings.get().notifications) return Result.success()
        if (System.currentTimeMillis() < dueAt) return Result.retry()
        if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val notifications = NotificationManagerCompat.from(app)
        if (!notifications.areNotificationsEnabled()) return Result.success()

        val open = PendingIntent.getActivity(app, scheduleId.hashCode(),
            Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_injections", true)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(app, "injection_reminders")
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("Lembrete de aplicação")
            .setContentText("Toque para conferir sua próxima aplicação no Mercado Fácil.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifications.notify(scheduleId.hashCode(), notification)
        return Result.success()
    }
}
