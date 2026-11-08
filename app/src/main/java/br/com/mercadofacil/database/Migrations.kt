package br.com.mercadofacil.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserves all lists, item selection and sync operations installed before v1.0.5. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `purchase_history` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `listId` TEXT NOT NULL,
                `listName` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `itemsJson` TEXT NOT NULL,
                `totalCents` INTEGER NOT NULL,
                `itemCount` INTEGER NOT NULL
            )
        """.trimIndent())
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE shopping_items ADD COLUMN photoData TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE shopping_items ADD COLUMN photoDirty INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `injection_schedules` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `medication` TEXT NOT NULL,
                `dose` TEXT NOT NULL,
                `route` TEXT NOT NULL,
                `intervalValue` INTEGER NOT NULL,
                `intervalUnit` TEXT NOT NULL,
                `nextDueAt` INTEGER NOT NULL,
                `responsible` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `active` INTEGER NOT NULL,
                `reminderEnabled` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `injection_records` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `scheduleId` TEXT NOT NULL,
                `scheduledForAt` INTEGER NOT NULL,
                `administeredAt` INTEGER NOT NULL,
                `dose` TEXT NOT NULL,
                `applicationSite` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                FOREIGN KEY(`scheduleId`) REFERENCES `injection_schedules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_injection_records_scheduleId` ON `injection_records` (`scheduleId`)")
    }
}
