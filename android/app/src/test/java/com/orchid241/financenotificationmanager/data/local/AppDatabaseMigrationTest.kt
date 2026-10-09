package com.orchid241.financenotificationmanager.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.parser.SupportedFinancialApps
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
class AppDatabaseMigrationTest {
    @Test fun migrationPreservesVersionOneRowsAndSupportsNewTransactions() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = javaClass.classLoader!!.getResourceAsStream("com.orchid241.financenotificationmanager.data.local.AppDatabase/1.json")!!.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO raw_notifications (id, notificationKey, packageName, title, text, postedAt, receivedAt, processingStatus) VALUES (7, 'old-key', 'com.example.other', NULL, NULL, 100, 101, 'PENDING')")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        try {
            val raw = migrated.rawNotificationDao().getAll().single()
            assertEquals(7L, raw.id)
            assertEquals("com.example.other", raw.packageName)
            assertNull(raw.title)
            assertNull(raw.text)
            assertEquals("PENDING", raw.processingStatus)
            assertTrue(migrated.financialTransactionDao().observeAll().first().isEmpty())
            assertTrue(migrated.consistencyCandidateDao().observeAll().first().isEmpty())
            FinancialTransactionRepository(migrated).collectNotification("new", SupportedFinancialApps.KAKAO_BANK, "입금 1,000원", "가상인물 → 입출금통장(5678)\n잔액 1,000원", 200, 201)
            assertEquals(2, migrated.rawNotificationDao().getAll().size)
            assertEquals(1000L, migrated.financialTransactionDao().observeAll().first().single().amount)
        } finally {
            migrated.close()
        }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
        try {
            assertEquals(2, reopened.rawNotificationDao().getAll().size)
            assertEquals(1, reopened.financialTransactionDao().observeAll().first().size)
            assertTrue(reopened.consistencyCandidateDao().observeAll().first().isEmpty())
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }
}
