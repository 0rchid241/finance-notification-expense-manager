package com.orchid241.financenotificationmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RawNotificationEntity::class,
        FinancialTransactionEntity::class,
        ConsistencyCandidateEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(TransactionTypeConverters::class, ConsistencyRelationTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawNotificationDao(): RawNotificationDao
    abstract fun financialTransactionDao(): FinancialTransactionDao
    abstract fun consistencyCandidateDao(): ConsistencyCandidateDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `consistency_candidates` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `firstTransactionId` INTEGER NOT NULL,
                        `secondTransactionId` INTEGER NOT NULL,
                        `relationType` TEXT NOT NULL,
                        `reasonText` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`firstTransactionId`) REFERENCES `financial_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`secondTransactionId`) REFERENCES `financial_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_consistency_candidates_firstTransactionId` ON `consistency_candidates` (`firstTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_consistency_candidates_secondTransactionId` ON `consistency_candidates` (`secondTransactionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_consistency_candidates_firstTransactionId_secondTransactionId_relationType` ON `consistency_candidates` (`firstTransactionId`, `secondTransactionId`, `relationType`)")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
