package br.com.mercadofacil.notifications

import android.content.Context
import androidx.work.*
import br.com.mercadofacil.MercadoApp
import br.com.mercadofacil.updater.notifyUser
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MercadoApp
        if (app.settings.get().notifications) notifyUser(app, "Hora de fazer as compras", "Confira suas listas no Mercado Fácil.", 20)
        return Result.success()
    }
    companion object {
        fun schedule(context: Context, hours: Long) {
            require(hours in 1..720)
            WorkManager.getInstance(context).enqueueUniqueWork("shopping-reminder", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(hours, TimeUnit.HOURS).build())
        }
        fun cancel(context: Context) { WorkManager.getInstance(context).cancelUniqueWork("shopping-reminder") }
    }
}
