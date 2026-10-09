package com.orchid241.financenotificationmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.orchid241.financenotificationmanager.data.FinancialTransactionRepository
import com.orchid241.financenotificationmanager.data.local.AppDatabase
import com.orchid241.financenotificationmanager.data.local.FinancialTransactionEntity
import com.orchid241.financenotificationmanager.ui.TransactionConsistencyUiState
import com.orchid241.financenotificationmanager.ui.TransactionListScreen
import com.orchid241.financenotificationmanager.ui.consistencyByTransaction
import com.orchid241.financenotificationmanager.ui.theme.FinanceNotificationManagerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine

class MainActivity : ComponentActivity() {
    private val repository by lazy { FinancialTransactionRepository(AppDatabase.getInstance(applicationContext)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by produceState(TransactionListState()) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        repository.observeAll()
                            .combine(repository.observeConsistencyCandidates()) { transactions, candidates ->
                                TransactionListState(
                                    transactions = transactions,
                                    consistency = consistencyByTransaction(candidates),
                                    loading = false,
                                )
                            }
                            .collect { value = it }
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
                    loading = state.loading,
                    failed = state.failed,
                )
            }
        }
    }
}

private data class TransactionListState(
    val transactions: List<FinancialTransactionEntity> = emptyList(),
    val consistency: Map<Long, TransactionConsistencyUiState> = emptyMap(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)
