package br.com.mercadofacil.viewmodel

import android.app.Application
import androidx.lifecycle.*
import br.com.mercadofacil.MercadoApp
import br.com.mercadofacil.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as MercadoApp
    val repository = app.repository
    val lists = repository.dao.lists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val items = repository.dao.items().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val purchases = repository.dao.purchases().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.dao.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), br.com.mercadofacil.data.AppSettings())
    val selectedList = app.settings.selectedList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val conflicts = repository.dao.conflicts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val queue = repository.dao.queueSize().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val message = MutableStateFlow<String?>(null)
    val draftPhoto = MutableStateFlow("")
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    init { viewModelScope.launch {
        for (request in syncRequests) {
            try { app.sync.sync() } catch (e: Exception) { if (e is CancellationException) throw e }
        }
    } }
    init { viewModelScope.launch {
        app.settings.flow.map { it.notifications }.distinctUntilChanged().collect {
            br.com.mercadofacil.notifications.PushNotifications.schedule(app)
        }
    } }
    init { run {
        repository.initialize()
        val list = repository.ensureShoppingList(app.settings.selectedListId())
        app.settings.selectList(list.id)
    } }
    fun run(block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() } catch (e: Exception) { if (e is CancellationException) throw e; message.value = e.message ?: "Não foi possível concluir" }
    }
    fun save(list: ShoppingList) = run { repository.save(list); scheduleSync() }
    fun save(item: ShoppingItem) = run { repository.save(item); scheduleSync() }
    fun requestSync() = scheduleSync()
    private fun scheduleSync() {
        syncRequests.trySend(Unit)
        androidx.work.WorkManager.getInstance(app).enqueueUniqueWork("sync-now", androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<br.com.mercadofacil.sync.SyncWorker>().setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build())
    }
}
