package com.orchid241.financenotificationmanager.mockwallet

import android.content.Context
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.data.local.RawNotificationEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.util.UUID

data class WalletScenario(val id: String, val label: String, val events: List<ScenarioEvent>, val expected: List<String>, val transactions: Int)
data class ScenarioEvent(val event: String, val text: String, val delay: Long, val key: String, val restart: Boolean)
data class ScenarioReport(val passed: Boolean, val statuses: List<String>, val rawCount: Int, val transactionCount: Int, val candidates: Int, val ruleMatches: Int)

object WalletScenarios {
    fun load(context: Context): List<WalletScenario> {
        return load(context.assets.open("wallet_fixture_scenarios.json").bufferedReader().use { it.readText() })
    }

    internal fun load(text: String): List<WalletScenario> {
        val json = JSONObject(text).getJSONArray("scenarios")
        return (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            val events = item.getJSONArray("events")
            val expected = item.getJSONArray("expected")
            WalletScenario(item.getString("id"), item.getString("label"), (0 until events.length()).map {
                val e = events.getJSONObject(it)
                ScenarioEvent(e.getString("event"), e.getString("text"), e.getLong("delay"), e.getString("key"), e.optBoolean("restart"))
            }, (0 until expected.length()).map { expected.getString(it) }, item.getInt("transactions"))
        }
    }

    suspend fun run(database: AppDatabase, scenario: WalletScenario): ScenarioReport {
        val repository = FinancialTransactionRepository(database)
        if (database.userRuleDao().getEnabled().none { it.name == "[TEST] 합성 금액 규칙" }) {
            repository.addAmountRule("[TEST] 합성 금액 규칙", 1000, "[TEST] 합성 알림 규칙 실행 확인")
        }
        val rawBefore = database.rawNotificationDao().getAll().size
        val txBefore = database.financialTransactionDao().getAll().size
        val candidateBefore = database.consistencyCandidateDao().getAll().size
        val ruleBefore = database.ruleMatchDao().observeAll().first().size
        val run = UUID.randomUUID().toString()
        val at = System.currentTimeMillis()
        var processor = SyntheticWalletProcessor(database)
        val statuses = scenario.events.map { e ->
            if (e.restart) processor = SyntheticWalletProcessor(database)
            processor.receive(RawNotificationEntity(notificationKey = "[TEST]-$run-${e.key}", packageName = SyntheticWalletParser.PACKAGE,
                title = "[TEST] wallet-v1|$run-${e.event}|$at|OUT|payment", text = e.text, postedAt = at, receivedAt = at + e.delay))
        }
        val rawCount = database.rawNotificationDao().getAll().size - rawBefore
        val txCount = database.financialTransactionDao().getAll().size - txBefore
        val candidates = database.consistencyCandidateDao().getAll().size - candidateBefore
        val matches = database.ruleMatchDao().observeAll().first().size - ruleBefore
        val passed = statuses == scenario.expected && rawCount == scenario.events.size && txCount == scenario.transactions &&
            matches >= scenario.transactions && (scenario.id != "distinct" || candidates > 0)
        return ScenarioReport(passed, statuses, rawCount, txCount, candidates, matches)
    }
}
