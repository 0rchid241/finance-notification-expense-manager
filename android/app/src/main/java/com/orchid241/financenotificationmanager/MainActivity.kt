package com.orchid241.financenotificationmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.data.local.RuleMatchEntity
import com.orchid241.financenotificationmanager.data.local.UserRuleEntity
import com.orchid241.financenotificationmanager.ui.AppBottomBar
import com.orchid241.financenotificationmanager.ui.AppTab
import com.orchid241.financenotificationmanager.ui.HomeScreen
import com.orchid241.financenotificationmanager.ui.TransactionConsistencyUiState
import com.orchid241.financenotificationmanager.ui.TransactionListScreen
import com.orchid241.financenotificationmanager.ui.consistencyByTransaction
import com.orchid241.financenotificationmanager.ui.theme.FinanceNotificationManagerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository by lazy { FinancialTransactionRepository(AppDatabase.getInstance(applicationContext)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by produceState(TransactionListState()) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        combine(
                            repository.observeAll(),
                            repository.observeConsistencyCandidates(),
                            repository.observeUserRules(),
                            repository.observeRuleMatches(),
                        ) { transactions, candidates, rules, matches ->
                            TransactionListState(
                                transactions = transactions,
                                consistency = consistencyByTransaction(candidates),
                                rules = rules,
                                ruleMatches = matches.groupBy(RuleMatchEntity::transactionId),
                                loading = false,
                            )
                        }.collect { value = it }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        value = TransactionListState(loading = false, failed = true)
                    }
                }
            }

            var selectedTab by remember { mutableStateOf(AppTab.HOME) }

            FinanceNotificationManagerTheme {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        AppBottomBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                        )
                    },
                ) { innerPadding ->
                    when (selectedTab) {
                        AppTab.HOME -> HomeScreen(
                            transactions = state.transactions,
                            consistency = state.consistency,
                            ruleMatches = state.ruleMatches,
                            loading = state.loading,
                            failed = state.failed,
                            modifier = Modifier.padding(innerPadding),
                        )

                        AppTab.TRANSACTIONS,
                        AppTab.RULES -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                        ) {
                            TransactionListScreen(
                                transactions = state.transactions,
                                consistency = state.consistency,
                                rules = state.rules,
                                ruleMatches = state.ruleMatches,
                                loading = state.loading,
                                failed = state.failed,
                                onAddAmountRule = { threshold ->
                                    lifecycleScope.launch {
                                        repository.addAmountRule(
                                            name = "큰 지출",
                                            amountThreshold = threshold,
                                            message = "설정한 금액 이상의 지출이 발생했어요.",
                                        )
                                    }
                                },
                                onAddCounterpartyRule = { keyword ->
                                    lifecycleScope.launch {
                                        repository.addCounterpartyRule(
                                            name = "상대방 알림",
                                            keyword = keyword,
                                            message = "설정한 상대방과의 거래가 발생했어요.",
                                        )
                                    }
                                },
                            )
                        }

                        AppTab.SETTINGS -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "설정 화면은 다음 단계에서 연결할게요.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class TransactionListState(
    val transactions: List<FinancialTransactionEntity> = emptyList(),
    val consistency: Map<Long, TransactionConsistencyUiState> = emptyMap(),
    val rules: List<UserRuleEntity> = emptyList(),
    val ruleMatches: Map<Long, List<RuleMatchEntity>> = emptyMap(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)
