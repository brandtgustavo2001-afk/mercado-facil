package br.com.mercadofacil.viewmodel

import android.app.Application
import androidx.lifecycle.*
import br.com.mercadofacil.MercadoApp
import br.com.mercadofacil.database.*
import br.com.mercadofacil.domain.nextInjectionDueAt
import br.com.mercadofacil.notifications.InjectionReminderScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as MercadoApp
    val repository = app.repository
    val lists = repository.dao.lists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val items = repository.dao.items().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val purchases = repository.dao.purchases().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val injectionSchedules = repository.dao.injectionSchedules().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val injectionRecords = repository.dao.injectionRecords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
        app.settings.flow.map { it.notifications }.distinctUntilChanged().collect { enabled ->
            br.com.mercadofacil.notifications.PushNotifications.schedule(app)
            val schedules = repository.dao.injectionSchedules().first()
            schedules.forEach { schedule ->
                if (enabled) InjectionReminderScheduler.schedule(app, schedule)
                else InjectionReminderScheduler.cancel(app, schedule.id)
            }
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
    fun saveInjection(schedule: InjectionSchedule) = run {
        require(schedule.name.isNotBlank()) { "Informe o nome desta aplicação." }
        nextInjectionDueAt(schedule.nextDueAt, schedule.intervalValue, schedule.intervalUnit)
        val saved = repository.saveInjection(schedule)
        if (saved.id != schedule.id) InjectionReminderScheduler.cancel(app, schedule.id)
        refreshInjectionReminder(saved)
        scheduleSync()
    }
    fun setInjectionReminder(schedule: InjectionSchedule, enabled: Boolean) = run {
        val saved = repository.setInjectionReminder(schedule.id, enabled)
        refreshInjectionReminder(saved)
    }
    fun deleteInjection(schedule: InjectionSchedule) = run {
        repository.deleteInjection(schedule)
        InjectionReminderScheduler.cancel(app, schedule.id)
        scheduleSync()
    }
    fun recordInjection(schedule: InjectionSchedule, record: InjectionRecord) = run {
        require(record.scheduleId == schedule.id) { "O registro não corresponde a esta aplicação." }
        val updated = schedule.copy(
            nextDueAt = nextInjectionDueAt(record.administeredAt, schedule.intervalValue, schedule.intervalUnit),
            updatedAt = System.currentTimeMillis()
        )
        repository.saveInjectionAndRecord(updated, record)
        refreshInjectionReminder(updated)
        scheduleSync()
    }
    fun updateInjectionRecord(schedule: InjectionSchedule, record: InjectionRecord) = run {
        val records = repository.dao.injectionRecordsFor(schedule.id)
        require(records.any { it.id == record.id }) { "Este registro não está mais disponível." }
        require(record.scheduleId == schedule.id) { "O registro não corresponde a esta aplicação." }
        val previousLatest = records.maxByOrNull { it.administeredAt }
        val latestAfterEdit = (records.filterNot { it.id == record.id } + record).maxByOrNull { it.administeredAt }
        val changesNextDue = previousLatest?.id == record.id || latestAfterEdit?.id == record.id
        val updated = if (changesNextDue && latestAfterEdit != null) schedule.copy(
            nextDueAt = nextInjectionDueAt(latestAfterEdit.administeredAt, schedule.intervalValue, schedule.intervalUnit),
            updatedAt = System.currentTimeMillis()
        ) else schedule
        repository.saveInjectionAndRecord(if (changesNextDue) updated else schedule, record)
        if (changesNextDue) refreshInjectionReminder(updated)
        scheduleSync()
    }
    fun deleteInjectionRecord(schedule: InjectionSchedule, record: InjectionRecord) = run {
        val records = repository.dao.injectionRecordsFor(schedule.id)
        require(records.any { it.id == record.id }) { "Este registro não está mais disponível." }
        val wasLatest = records.maxByOrNull { it.administeredAt }?.id == record.id
        val previous = records.filterNot { it.id == record.id }.maxByOrNull { it.administeredAt }
        val updated = if (wasLatest) schedule.copy(
            nextDueAt = previous?.let { nextInjectionDueAt(it.administeredAt, schedule.intervalValue, schedule.intervalUnit) } ?: record.scheduledForAt,
            updatedAt = System.currentTimeMillis()
        ) else schedule
        repository.saveInjectionAndDeleteRecord(if (wasLatest) updated else schedule, record)
        if (wasLatest) refreshInjectionReminder(updated)
        scheduleSync()
    }
    private suspend fun refreshInjectionReminder(schedule: InjectionSchedule) {
        if (app.settings.get().notifications) InjectionReminderScheduler.schedule(app, schedule)
        else InjectionReminderScheduler.cancel(app, schedule.id)
    }
    fun requestSync() = scheduleSync()
    private fun scheduleSync() {
        syncRequests.trySend(Unit)
        androidx.work.WorkManager.getInstance(app).enqueueUniqueWork("sync-now", androidx.work.ExistingWorkPolicy.KEEP,
            androidx.work.OneTimeWorkRequestBuilder<br.com.mercadofacil.sync.SyncWorker>().setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build())
    }
}
