package com.orchid241.financenotificationmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.orchid241.financenotificationmanager.ui.RulesScreen
import com.orchid241.financenotificationmanager.ui.SettingsScreen
import com.orchid241.financenotificationmanager.ui.TransactionConsistencyUiState
import com.orchid241.financenotificationmanager.ui.TransactionFilter
import com.orchid241.financenotificationmanager.ui.TransactionsScreen
import com.orchid241.financenotificationmanager.ui.consistencyByTransaction
import com.orchid241.financenotificationmanager.ui.theme.FinanceNotificationManagerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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
                            val enabledRuleIds = rules
                                .asSequence()
                                .filter(UserRuleEntity::enabled)
                                .map(UserRuleEntity::id)
                                .toSet()
                            val visibleMatches = matches
                                .filter { it.ruleId in enabledRuleIds }
                                .groupBy(RuleMatchEntity::transactionId)

                            TransactionListState(
                                transactions = transactions,
                                consistency = consistencyByTransaction(candidates),
                                rules = rules,
                                ruleMatches = visibleMatches,
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

            val preferences = remember { getSharedPreferences("app_settings", MODE_PRIVATE) }
            var darkMode by remember { mutableStateOf(preferences.getBoolean("dark_mode", false)) }
            var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME) }
            var transactionFilter by rememberSaveable { mutableStateOf(TransactionFilter.ALL) }
            var selectedTransactionId by rememberSaveable { mutableStateOf<Long?>(null) }

            FinanceNotificationManagerTheme(darkTheme = darkMode) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        AppBottomBar(
                            selectedTab = selectedTab,
                            onTabSelected = { tab ->
                                selectedTab = tab
                                selectedTransactionId = null
                            },
                        )
                    },
                ) { innerPadding ->
                    val screenModifier = Modifier.padding(innerPadding)
                    when (selectedTab) {
                        AppTab.HOME -> HomeScreen(
                            transactions = state.transactions,
                            consistency = state.consistency,
                            ruleMatches = state.ruleMatches,
                            loading = state.loading,
                            failed = state.failed,
                            onViewTransactions = { filter ->
                                transactionFilter = filter
                                selectedTransactionId = null
                                selectedTab = AppTab.TRANSACTIONS
                            },
                            onViewTransactionDetail = { transactionId ->
                                selectedTransactionId = transactionId
                                selectedTab = AppTab.TRANSACTIONS
                            },
                            modifier = screenModifier,
                        )

                        AppTab.TRANSACTIONS -> TransactionsScreen(
                            transactions = state.transactions,
                            consistency = state.consistency,
                            ruleMatches = state.ruleMatches,
                            loading = state.loading,
                            failed = state.failed,
                            selectedFilter = transactionFilter,
                            onFilterSelected = { transactionFilter = it },
                            selectedTransactionId = selectedTransactionId,
                            onTransactionSelected = { selectedTransactionId = it },
                            onDetailBack = { selectedTransactionId = null },
                            modifier = screenModifier,
                        )

                        AppTab.RULES -> RulesScreen(
                            rules = state.rules,
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
                            onToggleRule = { ruleId, enabled ->
                                lifecycleScope.launch { repository.setRuleEnabled(ruleId, enabled) }
                            },
                            onDeleteRule = { ruleId ->
                                lifecycleScope.launch { repository.deleteRule(ruleId) }
                            },
                            modifier = screenModifier,
                        )

                        AppTab.SETTINGS -> SettingsScreen(
                            darkMode = darkMode,
                            onDarkModeChanged = { enabled ->
                                darkMode = enabled
                                preferences.edit().putBoolean("dark_mode", enabled).apply()
                            },
                            onResetData = {
                                lifecycleScope.launch(Dispatchers.IO) { repository.clearAllData() }
                            },
                            modifier = screenModifier,
                        )
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
