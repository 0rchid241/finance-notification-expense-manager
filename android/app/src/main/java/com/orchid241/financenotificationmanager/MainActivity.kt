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
import com.orchid241.financenotificationmanager.ui.TransactionListScreen
import com.orchid241.financenotificationmanager.ui.theme.FinanceNotificationManagerTheme
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    private val repository by lazy { FinancialTransactionRepository(AppDatabase.getInstance(applicationContext)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by produceState(TransactionListState()) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    try {
                        repository.observeAll().collect { value = TransactionListState(transactions = it, loading = false) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        value = TransactionListState(loading = false, failed = true)
                    }
                }
            }
            FinanceNotificationManagerTheme {
                TransactionListScreen(state.transactions, loading = state.loading, failed = state.failed)
            }
        }
    }
}

private data class TransactionListState(
    val transactions: List<FinancialTransactionEntity> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)
