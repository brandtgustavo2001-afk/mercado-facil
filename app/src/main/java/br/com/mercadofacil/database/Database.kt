package br.com.mercadofacil.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "shopping_lists")
data class ShoppingList(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val market: String = "",
    val budgetCents: Long = 0,
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val shared: Boolean = false,
    val version: Long = 0,
    val deleted: Boolean = false
)
@Entity(tableName = "shopping_items", foreignKeys = [ForeignKey(entity = ShoppingList::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)], indices = [Index("listId"), Index("name")])
data class ShoppingItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val name: String,
    val quantity: String = "1",
    val unit: String = "un",
    val estimatedCents: Long = 0,
    val actualCents: Long? = null,
    val category: String = "Outros",
    val brand: String = "",
    val notes: String = "",
    val photoData: String = "",
    val photoDirty: Boolean = false,
    val purchased: Boolean = false,
    val favorite: Boolean = false,
    val position: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val purchasedBy: String? = null,
    val version: Long = 0,
    val deleted: Boolean = false
)
/** Immutable, local snapshot of a paid checkout. The history is limited to ten checkouts. */
@Entity(tableName = "purchase_history")
data class PurchaseHistory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val listName: String,
    val createdAt: Long,
    val itemsJson: String,
    val totalCents: Long,
    val itemCount: Int
)
/** A locally stored recurring injection reminder. It is intentionally not part of shopping-list sync. */
@Entity(tableName = "injection_schedules")
data class InjectionSchedule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val medication: String = "",
    val dose: String = "",
    val route: String = "",
    val intervalValue: Int = 1,
    val intervalUnit: String = "DAYS",
    val nextDueAt: Long,
    val responsible: String = "",
    val notes: String = "",
    val active: Boolean = true,
    val reminderEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
@Entity(
    tableName = "injection_records",
    foreignKeys = [ForeignKey(entity = InjectionSchedule::class, parentColumns = ["id"], childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("scheduleId")]
)
data class InjectionRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val scheduleId: String,
    val scheduledForAt: Long,
    val administeredAt: Long,
    val dose: String = "",
    val applicationSite: String = "",
    val notes: String = ""
)
@Entity(tableName = "categories")
data class Category(@PrimaryKey val name: String)
@Entity(tableName = "sync_operations", indices = [Index("entityId")])
data class SyncOperation(
    @PrimaryKey val operationId: String = UUID.randomUUID().toString(),
    val entityId: String,
    val listId: String,
    val entityType: String,
    val payload: String,
    val baseVersion: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: String = "pending",
    val error: String? = null
)
@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_lists WHERE deleted = 0 ORDER BY updatedAt DESC") fun lists(): Flow<List<ShoppingList>>
    @Query("SELECT * FROM shopping_items WHERE deleted = 0 ORDER BY purchased, position, name") fun items(): Flow<List<ShoppingItem>>
    @Query("SELECT * FROM purchase_history ORDER BY createdAt DESC, id DESC") fun purchases(): Flow<List<PurchaseHistory>>
    @Query("DELETE FROM purchase_history WHERE listId = :listId AND id NOT IN (:ids)") suspend fun reconcilePurchases(listId: String, ids: List<String>)
    @Query("SELECT * FROM purchase_history WHERE id = :id") suspend fun purchase(id: String): PurchaseHistory?
    @Upsert suspend fun save(purchase: PurchaseHistory)
    @Query("DELETE FROM purchase_history WHERE id NOT IN (SELECT id FROM purchase_history ORDER BY createdAt DESC, id DESC LIMIT 10)") suspend fun trimPurchaseHistory()
    @Query("SELECT * FROM injection_schedules ORDER BY active DESC, nextDueAt ASC") fun injectionSchedules(): Flow<List<InjectionSchedule>>
    @Query("SELECT * FROM injection_records ORDER BY administeredAt DESC") fun injectionRecords(): Flow<List<InjectionRecord>>
    @Query("SELECT * FROM injection_schedules WHERE id = :id") suspend fun injectionSchedule(id: String): InjectionSchedule?
    @Query("SELECT * FROM injection_records WHERE scheduleId = :scheduleId ORDER BY administeredAt ASC") suspend fun injectionRecordsFor(scheduleId: String): List<InjectionRecord>
    @Upsert suspend fun saveInjection(schedule: InjectionSchedule)
    @Upsert suspend fun saveInjectionRecord(record: InjectionRecord)
    @Query("DELETE FROM injection_schedules WHERE id = :id") suspend fun deleteInjectionSchedule(id: String)
    @Query("DELETE FROM injection_records WHERE id = :id") suspend fun deleteInjectionRecord(id: String)
    @Query("SELECT * FROM shopping_items WHERE listId = :listId AND purchased = 1 AND deleted = 0 ORDER BY position, name") suspend fun selectedItems(listId: String): List<ShoppingItem>
    @Query("SELECT * FROM categories ORDER BY name") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM shopping_lists") suspend fun allLists(): List<ShoppingList>
    @Query("SELECT * FROM shopping_items") suspend fun allItems(): List<ShoppingItem>
    @Query("SELECT * FROM shopping_lists WHERE id = :id") suspend fun list(id: String): ShoppingList?
    @Query("SELECT * FROM shopping_items WHERE id = :id") suspend fun item(id: String): ShoppingItem?
    @Upsert suspend fun save(list: ShoppingList)
    @Upsert suspend fun save(item: ShoppingItem)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun category(value: Category)
    @Query("DELETE FROM categories WHERE name = :name") suspend fun deleteCategory(name: String)
    @Insert suspend fun enqueue(op: SyncOperation)
    @Query("SELECT * FROM sync_operations WHERE status = 'pending' ORDER BY rowid") suspend fun pending(): List<SyncOperation>
    @Query("SELECT COUNT(*) FROM sync_operations") suspend fun totalQueued(): Int
    @Query("SELECT COUNT(*) FROM sync_operations WHERE entityId = :id AND status = 'conflict'") suspend fun conflicting(id: String): Int
    @Query("SELECT * FROM sync_operations WHERE status = 'conflict'") fun conflicts(): Flow<List<SyncOperation>>
    @Query("SELECT COUNT(*) FROM sync_operations") fun queueSize(): Flow<Int>
    @Query("DELETE FROM sync_operations WHERE operationId = :id") suspend fun ack(id: String)
    @Query("UPDATE sync_operations SET status = :status, error = :error, retryCount = retryCount + 1 WHERE operationId = :id") suspend fun failed(id: String, status: String, error: String)
    @Query("SELECT COUNT(*) FROM sync_operations WHERE entityId = :id") suspend fun queued(id: String): Int
}
@Database(entities = [ShoppingList::class, ShoppingItem::class, Category::class, SyncOperation::class, PurchaseHistory::class, InjectionSchedule::class, InjectionRecord::class], version = 4, exportSchema = true)
abstract class MercadoDatabase : RoomDatabase() { abstract fun dao(): ShoppingDao }

val defaultCategories = listOf("Açougue", "Hortifruti", "Padaria", "Laticínios", "Bebidas", "Mercearia", "Congelados", "Limpeza", "Higiene pessoal", "Pet Shop", "Farmácia", "Outros")
val units = listOf("un", "kg", "g", "L", "ml", "pct", "cx", "dz", "m")
