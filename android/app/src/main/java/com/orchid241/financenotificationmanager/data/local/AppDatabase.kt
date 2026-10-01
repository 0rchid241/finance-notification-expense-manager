package com.orchid241.financenotificationmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RawNotificationEntity::class, FinancialTransactionEntity::class], version = 2, exportSchema = true)
@TypeConverters(TransactionTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawNotificationDao(): RawNotificationDao
    abstract fun financialTransactionDao(): FinancialTransactionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `financial_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `rawNotificationId` INTEGER NOT NULL,
                        `transactionType` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `counterparty` TEXT NOT NULL,
                        `accountLast4` TEXT NOT NULL,
                        `balance` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`rawNotificationId`) REFERENCES `raw_notifications`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transactions_rawNotificationId` ON `financial_transactions` (`rawNotificationId`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "finance_notification_manager.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
