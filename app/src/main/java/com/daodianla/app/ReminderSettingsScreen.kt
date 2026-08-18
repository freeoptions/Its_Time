package com.daodianla.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    status: ReminderSystemStatus,
    logs: List<ReminderLogEntry>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onManualCheckChanged: (ManualReliabilityCheck, Boolean) -> Unit,
    onCopyLogs: () -> Unit,
    onShareLogs: () -> Unit
) {
    var copied by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DaoDianLaColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "提醒可靠性",
                        color = DaoDianLaColors.ink,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = DaoDianLaColors.ink
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            copied = false
                            onRefresh()
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "重新检查",
                            tint = DaoDianLaColors.blue
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DaoDianLaColors.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { ReliabilitySummaryCard(status) }

            item { SettingsSectionTitle("系统自动检测", "状态来自 Android 系统") }
            item {
                AutomaticSettingCard(
                    icon = Icons.Outlined.NotificationsNone,
                    title = "通知权限与提醒渠道",
                    description = if (status.reliability.notificationsAllowed) {
                        "通知总开关和提醒渠道均可用"
                    } else {
                        "闹钟可能触发，但通知无法显示"
                    },
                    ready = status.reliability.notificationsAllowed,
                    onOpenSettings = onOpenNotificationSettings
                )
            }
            item {
                AutomaticSettingCard(
                    icon = Icons.Outlined.Schedule,
                    title = "精确闹钟",
                    description = if (status.reliability.exactAlarmAllowed) {
                        "允许系统在锁屏和 Doze 中准点唤醒"
                    } else {
                        "未授权时，锁屏提醒可能明显延迟"
                    },
                    ready = status.reliability.exactAlarmAllowed,
                    onOpenSettings = onOpenExactAlarmSettings
                )
            }

            item { SettingsSectionTitle("小米人工确认", "系统不提供可靠查询接口") }
            item {
                ManualSettingCard(
                    icon = Icons.Outlined.Repeat,
                    title = "允许自启动",
                    description = "在小米自启动管理中允许“到点啦”，返回后勾选确认",
                    confirmed = status.autostartConfirmed,
                    onOpenSettings = onOpenAutostartSettings,
                    onConfirmedChange = {
                        onManualCheckChanged(ManualReliabilityCheck.AUTOSTART, it)
                    }
                )
            }
            item {
                ManualSettingCard(
                    icon = Icons.Outlined.BatterySaver,
                    title = "电量策略设为无限制",
                    description = if (status.batteryOptimizationIgnored) {
                        "Android 电池优化已豁免；仍请确认小米电量策略为“无限制”"
                    } else {
                        "打开应用耗电管理，选择“无限制”，返回后勾选确认"
                    },
                    confirmed = status.unrestrictedBatteryConfirmed,
                    onOpenSettings = onOpenBatterySettings,
                    onConfirmedChange = {
                        onManualCheckChanged(ManualReliabilityCheck.UNRESTRICTED_BATTERY, it)
                    }
                )
            }
            item {
                ManualSettingCard(
                    icon = Icons.Outlined.Lock,
                    title = "最近任务卡上锁",
                    description = "打开最近任务，为“到点啦”加小锁；不要向上划掉任务卡",
                    confirmed = status.recentsLockedConfirmed,
                    onOpenSettings = null,
                    onConfirmedChange = {
                        onManualCheckChanged(ManualReliabilityCheck.RECENTS_LOCKED, it)
                    }
                )
            }

            item { SettingsSectionTitle("诊断日志", "漏提醒时复制给开发者排查") }
            item {
                DiagnosticLogCard(
                    logs = logs,
                    copied = copied,
                    onCopy = {
                        onCopyLogs()
                        copied = true
                    },
                    onShare = onShareLogs
                )
            }
        }
    }
}

@Composable
private fun ReliabilitySummaryCard(status: ReminderSystemStatus) {
    val background = if (status.fullyReady) DaoDianLaColors.blueTint else DaoDianLaColors.warningTint
    val accent = if (status.fullyReady) DaoDianLaColors.blue else DaoDianLaColors.warning
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (status.fullyReady) Icons.Outlined.CheckCircleOutline else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (status.fullyReady) "提醒环境已准备好" else "还有设置需要确认",
                    color = DaoDianLaColors.ink,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "系统检测 ${status.automaticReadyCount}/2 · 人工确认 ${status.manualReadyCount}/3",
                    color = DaoDianLaColors.muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String, description: String) {
    Column(modifier = Modifier.padding(top = 4.dp, start = 2.dp)) {
        Text(
            title,
            color = DaoDianLaColors.ink,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(2.dp))
        Text(description, color = DaoDianLaColors.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AutomaticSettingCard(
    icon: ImageVector,
    title: String,
    description: String,
    ready: Boolean,
    onOpenSettings: () -> Unit
) {
    SettingStatusCard(
        icon = icon,
        title = title,
        description = description,
        statusLabel = if (ready) "已开启" else "待处理",
        ready = ready,
        trailing = {
            OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(12.dp)) {
                Text(if (ready) "查看" else "去开启")
            }
        }
    )
}

@Composable
private fun ManualSettingCard(
    icon: ImageVector,
    title: String,
    description: String,
    confirmed: Boolean,
    onOpenSettings: (() -> Unit)?,
    onConfirmedChange: (Boolean) -> Unit
) {
    SettingStatusCard(
        icon = icon,
        title = title,
        description = description,
        statusLabel = if (confirmed) "已人工确认" else "待人工确认",
        ready = confirmed,
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onOpenSettings != null) {
                    OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(12.dp)) {
                        Text("去设置")
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "我已完成",
                    color = DaoDianLaColors.muted,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.width(8.dp))
                Switch(checked = confirmed, onCheckedChange = onConfirmedChange)
            }
        }
    )
}

@Composable
private fun SettingStatusCard(
    icon: ImageVector,
    title: String,
    description: String,
    statusLabel: String,
    ready: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DaoDianLaColors.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (ready) DaoDianLaColors.blueTint else DaoDianLaColors.warningTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (ready) DaoDianLaColors.blue else DaoDianLaColors.warning,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = DaoDianLaColors.ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        statusLabel,
                        color = if (ready) DaoDianLaColors.blue else DaoDianLaColors.warning,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(10.dp))
            Text(description, color = DaoDianLaColors.muted, style = MaterialTheme.typography.bodySmall)
            footer?.let {
                Spacer(Modifier.height(12.dp))
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DaoDianLaColors.line)
                )
                Spacer(Modifier.height(8.dp))
                it()
            }
        }
    }
}

@Composable
private fun DiagnosticLogCard(
    logs: List<ReminderLogEntry>,
    copied: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DaoDianLaColors.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(DaoDianLaColors.blueTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = DaoDianLaColors.blue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("最近事件", color = DaoDianLaColors.ink, fontWeight = FontWeight.Bold)
                    Text(
                        "本机最多保存 120 条，不记录提醒内容",
                        color = DaoDianLaColors.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            if (logs.isEmpty()) {
                Text("暂无事件日志", color = DaoDianLaColors.muted, style = MaterialTheme.typography.bodySmall)
            } else {
                logs.forEachIndexed { index, entry ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    LogEntryRow(entry)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "已复制" else "复制日志")
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("分享日志")
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: ReminderLogEntry) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (entry.type in setOf(
                            ReminderLogType.SCHEDULE_FAILED,
                            ReminderLogType.NOTIFICATION_FAILED
                        )
                    ) DaoDianLaColors.warning else DaoDianLaColors.blue
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.type.label,
                    color = DaoDianLaColors.ink,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.weight(1f))
                Text(
                    ReminderEventLog.formatTimestamp(entry.timestamp).substring(5),
                    color = DaoDianLaColors.muted,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                entry.details,
                color = DaoDianLaColors.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
