package br.com.mercadofacil.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import br.com.mercadofacil.MainActivity
import br.com.mercadofacil.MercadoApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.UUID

object PushNotifications {
    fun preferences(context: Context) = context.getSharedPreferences("push-registration", Context.MODE_PRIVATE)
    fun installation(context: Context): String {
        val prefs = preferences(context)
        return prefs.getString("installation", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("installation", it).commit() }
    }
    fun initialize(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("shared_changes", "Alterações nas listas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Novos itens e compras concluídas por outros participantes"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            preferences(context).edit().putString("token", token).apply()
            schedule(context)
        }
    }
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("register-push", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PushRegistrationWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}
class PushRegistrationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MercadoApp
        if (app.sync.email.value.isBlank()) return Result.success()
        val token = PushNotifications.preferences(app).getString("token", null) ?: return Result.success()
        return try {
            val allowed = app.settings.get().notifications && NotificationManagerCompat.from(app).areNotificationsEnabled()
            app.sync.rpc("register_push", JSONObject().put("installation", PushNotifications.installation(app))
                .put("device_token", token).put("notifications_enabled", allowed))
            PushNotifications.preferences(app).edit().putString("registered_email", app.sync.email.value).apply()
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.retry()
        }
    }
}
class ShoppingMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushNotifications.preferences(this).edit().putString("token", token).remove("registered_email").apply()
        PushNotifications.schedule(this)
    }
    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as MercadoApp
        val prefs = PushNotifications.preferences(this)
        if (app.sync.email.value.isBlank() || prefs.getString("registered_email", "") != app.sync.email.value) return
        val listId = message.data["list_id"] ?: return
        val eventId = message.data["event_id"] ?: return
        try { UUID.fromString(listId); UUID.fromString(eventId) } catch (_: IllegalArgumentException) { return }
        WorkManager.getInstance(this).enqueueUniqueWork("push-event-$eventId", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PushEventWorker>().setInputData(workDataOf("list_id" to listId, "event_id" to eventId,
                "kind" to message.data["kind"].orEmpty())).build())
    }
}
class PushEventWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MercadoApp
        val listId = inputData.getString("list_id") ?: return Result.success()
        if (!app.settings.get().notifications || app.sync.email.value.isBlank()) return Result.success()
        val list = app.repository.dao.list(listId) ?: return Result.success()
        if (!list.shared || list.deleted) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val eventId = inputData.getString("event_id").orEmpty()
        val received = app.getSharedPreferences("push-received", Context.MODE_PRIVATE)
        if (received.getBoolean(eventId, false)) return Result.success()
        val open = PendingIntent.getActivity(app, eventId.hashCode(), Intent(app, MainActivity::class.java)
            .putExtra("shared_list_id", listId), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (inputData.getString("kind") == "purchase") "Uma compra foi concluída na sua lista compartilhada." else "Sua lista compartilhada foi atualizada."
        NotificationManagerCompat.from(app).notify(eventId, 0, NotificationCompat.Builder(app,"shared_changes")
            .setSmallIcon(android.R.drawable.ic_menu_agenda).setContentTitle("Mercado Fácil").setContentText(text)
            .setContentIntent(open).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
        received.edit().putBoolean(eventId, true).apply()
        WorkManager.getInstance(app).enqueueUniqueWork("sync-now",ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<br.com.mercadofacil.sync.SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        return Result.success()
    }
}
