package br.com.mercadofacil

import android.app.Application
import androidx.room.Room
import androidx.work.*
import br.com.mercadofacil.database.MercadoDatabase
import br.com.mercadofacil.repository.ShoppingRepository
import br.com.mercadofacil.data.Settings
import br.com.mercadofacil.sync.SyncEngine
import br.com.mercadofacil.sync.SyncWorker
import br.com.mercadofacil.updater.UpdateWorker
import java.util.concurrent.TimeUnit

class MercadoApp : Application() {
    val db by lazy { Room.databaseBuilder(this, MercadoDatabase::class.java, "mercado.db").build() }
    val repository by lazy { ShoppingRepository(db) }
    val settings by lazy { Settings(this) }
    val sync by lazy { SyncEngine(this, repository, settings) }
    override fun onCreate() {
        super.onCreate()
        val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("updates", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS).setConstraints(connected).build())
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(connected).build())
    }
}
