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
        UserRuleEntity::class,
        RuleMatchEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(
    TransactionTypeConverters::class,
    ConsistencyRelationTypeConverters::class,
    RuleConditionTypeConverters::class,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawNotificationDao(): RawNotificationDao
    abstract fun financialTransactionDao(): FinancialTransactionDao
    abstract fun consistencyCandidateDao(): ConsistencyCandidateDao
    abstract fun userRuleDao(): UserRuleDao
    abstract fun ruleMatchDao(): RuleMatchDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `conditionType` TEXT NOT NULL,
                        `amountThreshold` INTEGER,
                        `keyword` TEXT,
                        `message` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `rule_matches` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ruleId` INTEGER NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `ruleName` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`ruleId`) REFERENCES `user_rules`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`transactionId`) REFERENCES `financial_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rule_matches_ruleId` ON `rule_matches` (`ruleId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rule_matches_transactionId` ON `rule_matches` (`transactionId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rule_matches_ruleId_transactionId` ON `rule_matches` (`ruleId`, `transactionId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 부모 테이블 교체 시 CASCADE로 연결 데이터가 유실되지 않도록 먼저 복사한다.
                db.execSQL("CREATE TEMP TABLE saved_candidates AS SELECT * FROM consistency_candidates")
                db.execSQL("CREATE TEMP TABLE saved_matches AS SELECT * FROM rule_matches")
                db.execSQL("CREATE TEMP TABLE saved_sequences AS SELECT * FROM sqlite_sequence")
                db.execSQL("""
                    CREATE TABLE `financial_transactions_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `rawNotificationId` INTEGER,
                        `transactionType` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `counterparty` TEXT NOT NULL,
                        `accountLast4` TEXT NOT NULL,
                        `balance` INTEGER,
                        `source` TEXT NOT NULL,
                        `occurredAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`rawNotificationId`) REFERENCES `raw_notifications`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO financial_transactions_new SELECT * FROM financial_transactions")
                // Room이 Migration 전체를 트랜잭션으로 실행한다. 원본과 규칙 테이블은 변경하지 않는다.
                db.execSQL("DROP TABLE consistency_candidates")
                db.execSQL("DROP TABLE rule_matches")
                db.execSQL("DROP TABLE financial_transactions")
                db.execSQL("ALTER TABLE financial_transactions_new RENAME TO financial_transactions")
                db.execSQL("CREATE INDEX `index_financial_transactions_rawNotificationId` ON `financial_transactions` (`rawNotificationId`)")
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
                db.execSQL("INSERT INTO consistency_candidates SELECT * FROM saved_candidates")
                db.execSQL("INSERT INTO rule_matches SELECT * FROM saved_matches")
                // 삭제된 마지막 ID가 있어도 자동 생성 ID를 다시 사용하지 않는다.
                db.execSQL("""
                    INSERT INTO sqlite_sequence(name, seq)
                    SELECT name, seq FROM saved_sequences
                    WHERE name IN ('financial_transactions', 'consistency_candidates', 'rule_matches')
                    AND NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE sqlite_sequence.name = saved_sequences.name)
                """.trimIndent())
                db.execSQL("""
                    UPDATE sqlite_sequence SET seq = MAX(seq, COALESCE(
                        (SELECT saved_sequences.seq FROM saved_sequences WHERE saved_sequences.name = sqlite_sequence.name), seq
                    )) WHERE name IN ('financial_transactions', 'consistency_candidates', 'rule_matches')
                """.trimIndent())
                db.execSQL("DROP TABLE saved_candidates")
                db.execSQL("DROP TABLE saved_matches")
                db.execSQL("DROP TABLE saved_sequences")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE consistency_candidates ADD COLUMN reviewStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE consistency_candidates ADD COLUMN isActive INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE consistency_candidates ADD COLUMN reviewedAt INTEGER DEFAULT NULL")
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}
