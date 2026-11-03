package com.orchid241.financenotificationmanager.mockwallet

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.Room
import com.orchid241.financenotificationmanager.BuildConfig
import com.orchid241.financenotificationmanager.data.local.*
import com.orchid241.financenotificationmanager.ui.TransactionRow
import com.orchid241.financenotificationmanager.ui.consistencyByTransaction
import com.orchid241.financenotificationmanager.ui.theme.FinanceNotificationManagerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 운영 DB나 .dev의 실수집 DB를 열지 않는다. 테스트 DB에는 이 Activity만 쓴다. */
internal object WalletMockDatabase {
    private var instance: AppDatabase? = null
    @Synchronized fun open(context: Context): AppDatabase {
        check(BuildConfig.BUILD_TYPE == "externalDebug" && context.packageName == "${BuildConfig.APPLICATION_ID}" && context.packageName.endsWith(".dev"))
        return instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "wallet_mock_scenarios.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
            .build().also { instance = it }
    }
}

class WalletMockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = WalletMockDatabase.open(this)
        val scenarios = WalletScenarios.load(this)
        setContent {
            FinanceNotificationManagerTheme {
                val scope = rememberCoroutineScope()
                var running by remember { mutableStateOf(false) }
                var reports by remember { mutableStateOf(listOf<String>()) }
                val data by produceState(MockScreenData()) {
                    combine(database.financialTransactionDao().observeAll(), database.consistencyCandidateDao().observeAll(),
                        database.ruleMatchDao().observeAll()) { tx, candidates, matches -> MockScreenData(tx, candidates, matches) }
                        .collect { value = it }
                }
                Scaffold { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text("[TEST] 삼성월렛 합성 알림", style = MaterialTheme.typography.headlineSmall)
                            Text("실제 삼성월렛 형식·지원이 아닙니다. 별도 테스트 DB만 사용하며 가계부 월 집계에 합산하지 않습니다.")
                            Text("검증 결과 성공 ${reports.count { it.startsWith("성공") }} / ${reports.size}")
                            TextButton(onClick = { finish() }, enabled = !running) { Text("설정으로 돌아가기") }
                        }
                        item {
                            TextButton(enabled = !running, onClick = {
                                running = true
                                scope.launch {
                                    try {
                                        reports = withContext(Dispatchers.IO) {
                                            scenarios.map { scenario ->
                                                val report = WalletScenarios.run(database, scenario)
                                                "${if (report.passed) "성공" else "실패"} · ${scenario.label}: Raw ${report.rawCount}, 거래 ${report.transactionCount}, 후보 ${report.candidates}, 규칙 ${report.ruleMatches} · ${report.statuses.joinToString()}"
                                            }
                                        }
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { reports = listOf("실행 오류: DB 상태를 확인한 뒤 다시 시도하세요.") }
                                    finally { running = false }
                                }
                            }) { Text(if (running) "검증 중…" else "전체 시나리오 실행") }
                        }
                        items(scenarios, key = { it.id }) { scenario ->
                            TextButton(enabled = !running, onClick = {
                                running = true
                                scope.launch {
                                    try {
                                        val report = withContext(Dispatchers.IO) { WalletScenarios.run(database, scenario) }
                                        reports = listOf("${if (report.passed) "성공" else "실패"} · ${scenario.label}: Raw ${report.rawCount}, 거래 ${report.transactionCount}, 후보 ${report.candidates}, 규칙 ${report.ruleMatches} · ${report.statuses.joinToString()}")
                                    } catch (cancelled: CancellationException) { throw cancelled }
                                    catch (_: Exception) { reports = listOf("실행 오류: 다시 시도하세요.") }
                                    finally { running = false }
                                }
                            }) { Text(scenario.label) }
                        }
                        items(reports) { Text(it, style = MaterialTheme.typography.bodySmall) }
                        item { Text("저장된 테스트 거래 ${data.transactions.size}건 · 후보 ${data.candidates.size}건 · 규칙 결과 ${data.matches.size}건", style = MaterialTheme.typography.titleMedium) }
                        items(data.transactions, key = { it.id }) {
                            TransactionRow(it, consistencyByTransaction(data.candidates)[it.id], data.matches.filter { match -> match.transactionId == it.id })
                        }
                    }
                }
            }
        }
    }
}

private data class MockScreenData(val transactions: List<FinancialTransactionEntity> = emptyList(), val candidates: List<ConsistencyCandidateEntity> = emptyList(), val matches: List<RuleMatchEntity> = emptyList())
