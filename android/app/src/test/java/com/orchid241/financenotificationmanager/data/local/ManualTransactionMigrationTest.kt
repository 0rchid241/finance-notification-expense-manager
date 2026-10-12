package com.orchid241.financenotificationmanager.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.orchid241.financenotificationmanager.consistency.ConsistencyRelationType
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.ManualTransactionInput
import com.orchid241.financenotificationmanager.parser.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ManualTransactionMigrationTest {
    @Test fun versionTwoPreservesTransactions() = verifyMigration(2)
    @Test fun versionThreePreservesCandidates() = verifyMigration(3)
    @Test fun versionFourPreservesAllLinkedDataAndAutoincrement() = verifyMigration(4)

    private fun verifyMigration(version: Int) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "manual-migration-$version.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).also { it.parentFile!!.mkdirs() }
        val schema = javaClass.classLoader!!.getResourceAsStream("com.orchid241.financenotificationmanager.data.local.AppDatabase/$version.json")!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.setForeignKeyConstraintsEnabled(true)
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO raw_notifications VALUES (11, '기존 키', 'com.example.bank', '출금', '예전 원문', 100, 101, 'PROCESSED')")
            old.execSQL("INSERT INTO financial_transactions VALUES (21, 11, 'WITHDRAWAL', 10000, '가상매장', '0001', 50000, '카카오뱅크', 1000, 1001)")
            old.execSQL("INSERT INTO financial_transactions VALUES (22, 11, 'DEPOSIT', 10000, '가상매장', '0001', 60000, '카카오뱅크', 1100, 1101)")
            old.execSQL("INSERT INTO financial_transactions VALUES (100, 11, 'DEPOSIT', 1, '삭제된 테스트 거래', '0001', 0, '카카오뱅크', 1, 1)")
            old.execSQL("DELETE FROM financial_transactions WHERE id = 100")
            if (version >= 3) old.execSQL("INSERT INTO consistency_candidates VALUES (41, 21, 22, 'REFUND_CANDIDATE', '기존 사유', 1102)")
            if (version >= 4) {
                old.execSQL("INSERT INTO user_rules VALUES (31, '기존 규칙', 1, 'EXPENSE_AMOUNT_AT_LEAST', 1000, NULL, '기존 알림', 1103)")
                old.execSQL("INSERT INTO rule_matches VALUES (51, 31, 21, '기존 규칙', '기존 알림', 1104)")
            }
            old.version = version
        }
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
        val migrated = open()
        try {
            assertEquals(RawNotificationEntity(11, "기존 키", "com.example.bank", "출금", "예전 원문", 100, 101, "PROCESSED"), migrated.rawNotificationDao().getAll().single())
            assertEquals(listOf(
                FinancialTransactionEntity(22, 11, TransactionType.DEPOSIT, 10000, "가상매장", "0001", 60000, "카카오뱅크", 1100, 1101),
                FinancialTransactionEntity(21, 11, TransactionType.WITHDRAWAL, 10000, "가상매장", "0001", 50000, "카카오뱅크", 1000, 1001),
            ), migrated.financialTransactionDao().getAll())
            val candidates = migrated.consistencyCandidateDao().getAll()
            if (version >= 3) assertEquals(listOf(ConsistencyCandidateEntity(41, 21, 22, ConsistencyRelationType.REFUND_CANDIDATE, "기존 사유", 1102)), candidates)
            else assertTrue(candidates.isEmpty())
            if (version >= 4) {
                assertEquals(31L, migrated.userRuleDao().getEnabled().single().id)
                assertEquals("기존 규칙", migrated.userRuleDao().getEnabled().single().name)
                assertEquals(RuleMatchEntity(51, 31, 21, "기존 규칙", "기존 알림", 1104), migrated.ruleMatchDao().observeAll().first().single())
            }
            val manualId = FinancialTransactionRepository(migrated).addManualTransaction(ManualTransactionInput(2000, TransactionType.DEPOSIT, 100, "수동 상대방"))
            assertEquals(101L, manualId)
            val manual = migrated.financialTransactionDao().getAll().first()
            assertNull(manual.rawNotificationId)
            assertNull(manual.balance)
            migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { migrated.close() }
        val reopened = open()
        try {
            assertEquals(3, reopened.financialTransactionDao().getAll().size)
            assertEquals(1, reopened.rawNotificationDao().getAll().size)
            assertEquals(if (version >= 3) 1 else 0, reopened.consistencyCandidateDao().getAll().size)
            assertEquals(if (version >= 4) 1 else 0, reopened.ruleMatchDao().observeAll().first().size)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
}
