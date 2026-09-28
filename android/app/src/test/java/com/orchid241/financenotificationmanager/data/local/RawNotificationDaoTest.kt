package com.orchid241.financenotificationmanager.data.local

import androidx.room.Room
import com.orchid241.financenotificationmanager.data.RawNotificationRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class RawNotificationDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: RawNotificationDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).build()
        dao = database.rawNotificationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndReadPreservesEveryField() = runBlocking {
        val event = event()
        val id = dao.insert(event)

        assertTrue(id > 0)
        assertEquals(listOf(event.copy(id = id)), dao.getAll())
    }

    @Test
    fun statusQueryReturnsOnlyMatchingEvents() = runBlocking {
        val pending = event()
        val processed = event().copy(processingStatus = "PROCESSED")
        val pendingId = dao.insert(pending)
        val processedId = dao.insert(processed)

        assertEquals(listOf(pending.copy(id = pendingId)), dao.getByProcessingStatus("PENDING"))
        assertEquals(listOf(processed.copy(id = processedId)), dao.getByProcessingStatus("PROCESSED"))
        assertTrue(dao.getByProcessingStatus("UNKNOWN").isEmpty())
    }

    @Test
    fun identicalNotificationsAreStoredAsSeparateRows() = runBlocking {
        val event = event()
        val firstId = dao.insert(event)
        val secondId = dao.insert(event)

        assertTrue(firstId != secondId)
        assertEquals(listOf(event.copy(id = firstId), event.copy(id = secondId)), dao.getAll())
    }

    @Test
    fun nullableTitleAndTextArePreserved() = runBlocking {
        val event = event().copy(title = null, text = null)
        val id = dao.insert(event)

        assertEquals(listOf(event.copy(id = id)), dao.getAll())
    }

    @Test
    fun repositorySavesPendingEventsWithoutPackageFilteringOrDeduplication() = runBlocking {
        val repository = RawNotificationRepository(dao)
        val event = event().copy(packageName = "com.example.nonfinancial")
        repeat(2) {
            repository.saveRawNotification(
                notificationKey = event.notificationKey,
                packageName = event.packageName,
                title = event.title,
                text = event.text,
                postedAt = event.postedAt,
                receivedAt = event.receivedAt,
            )
        }

        val saved = repository.getByProcessingStatus("PENDING")
        assertEquals(2, saved.size)
        assertTrue(saved[0].id != saved[1].id)
        assertEquals(listOf(event, event), saved.map { it.copy(id = 0) })
        assertEquals(saved, repository.getAll())
    }

    private fun event() = RawNotificationEntity(
        notificationKey = "0|com.example.bank|42|null|10001",
        packageName = "com.example.bank",
        title = "카드 승인",
        text = "테스트 가맹점 12,000원",
        postedAt = 1_790_000_000_000L,
        receivedAt = 1_790_000_000_123L,
    )
}
