package com.orchid241.financenotificationmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.data.local.RuleMatchEntity
import com.orchid241.financenotificationmanager.data.local.UserRuleEntity
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
            FinanceNotificationManagerTheme {
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
