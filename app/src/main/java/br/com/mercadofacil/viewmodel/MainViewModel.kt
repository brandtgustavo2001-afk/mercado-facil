package br.com.mercadofacil.viewmodel

import android.app.Application
import androidx.lifecycle.*
import br.com.mercadofacil.MercadoApp
import br.com.mercadofacil.database.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as MercadoApp
    val repository = app.repository
    val lists = repository.dao.lists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val items = repository.dao.items().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.dao.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings = app.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), br.com.mercadofacil.data.AppSettings())
    val conflicts = repository.dao.conflicts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val queue = repository.dao.queueSize().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val message = MutableStateFlow<String?>(null)
    init { run { repository.initialize() } }
    fun run(block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() } catch (e: Exception) { if (e is CancellationException) throw e; message.value = e.message ?: "Não foi possível concluir" }
    }
    fun save(list: ShoppingList) = run { repository.save(list); scheduleSync() }
    fun save(item: ShoppingItem) = run { repository.save(item); scheduleSync() }
    private fun scheduleSync() {
        androidx.work.WorkManager.getInstance(app).enqueueUniqueWork("sync-now", androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
            androidx.work.OneTimeWorkRequestBuilder<br.com.mercadofacil.sync.SyncWorker>().setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build())
    }
}
