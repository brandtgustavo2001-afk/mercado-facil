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
