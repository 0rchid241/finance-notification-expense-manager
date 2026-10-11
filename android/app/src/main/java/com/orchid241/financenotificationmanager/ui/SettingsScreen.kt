package com.orchid241.financenotificationmanager.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

@Composable
fun SettingsScreen(
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    onResetData: () -> Unit,
    showDeveloperTools: Boolean = false,
    onAddRefundDemoData: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDemoData by remember { mutableStateOf(false) }
    val notificationAccessEnabled = remember {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text("설정", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        item {
            SettingsSection(title = "알림 수집") {
                SettingRow(
                    title = "알림 접근 권한",
                    description = if (notificationAccessEnabled) "허용됨 · 금융 알림을 수집할 수 있어요" else "권한이 필요해요",
                    trailing = if (notificationAccessEnabled) "허용됨" else "설정",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                )
                SettingRow(
                    title = "지원 금융 앱",
                    description = "현재 카카오뱅크 알림 파서를 지원해요",
                    trailing = "1개",
                )
            }
        }

        item {
            SettingsSection(title = "화면") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("다크 모드", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            text = "기본은 라이트 모드예요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = darkMode, onCheckedChange = onDarkModeChanged)
                }
            }
        }

        if (showDeveloperTools) {
            item {
                SettingsSection(title = "개발 도구") {
                    SettingRow(
                        title = "취소·환불 시연 데이터 생성",
                        description = "8,000원 출금 후 같은 금액이 돌아오는 테스트 거래 2건을 추가해요",
                        trailing = "생성",
                        onClick = { confirmDemoData = true },
                    )
                }
            }
        }

        item {
            SettingsSection(title = "데이터") {
                SettingRow(
                    title = "모든 데이터 초기화",
                    description = "저장된 알림, 거래, 규칙을 모두 삭제해요",
                    trailing = "초기화",
                    danger = true,
                    onClick = { confirmReset = true },
                )
            }
        }

        item {
            SettingsSection(title = "앱 정보") {
                SettingRow(title = "버전", description = "현재 개발 버전", trailing = "1.0")
                SettingRow(title = "저장 방식", description = "금융 데이터는 기기 내부 Room DB에 저장돼요", trailing = "로컬")
            }
        }
    }

    if (confirmDemoData) {
        AlertDialog(
            onDismissRequest = { confirmDemoData = false },
            title = { Text("시연 데이터를 만들까?") },
            text = {
                Text(
                    "스타벅스 정릉점 8,000원 출금과 1시간 뒤 동일 금액 입금 거래를 추가해. " +
                        "앱이 두 거래를 취소·환불 후보로 판단하는지 바로 확인할 수 있어.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDemoData = false
                        onAddRefundDemoData()
                    },
                ) { Text("생성") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDemoData = false }) { Text("취소") }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("모든 데이터를 지울까?") },
            text = { Text("저장된 원본 알림, 거래, 정합성 후보, 사용자 규칙이 모두 삭제돼. 이 작업은 되돌릴 수 없어.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetData()
                    },
                ) { Text("초기화", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    trailing: String,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier.padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
