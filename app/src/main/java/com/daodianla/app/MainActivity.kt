package com.daodianla.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            DaoDianLaTheme {
                DaoDianLaApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ReminderEventLog.append(
            applicationContext,
            ReminderLogType.APP_FOREGROUND,
            "应用界面进入前台"
        )
    }
}

@Composable
private fun DaoDianLaApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { ReminderRepository(context.applicationContext) }
    var reminders by remember { mutableStateOf(repository.getAll()) }
    var reliabilityStatus by remember {
        mutableStateOf(ReminderNotifications.status(context))
    }
    var diagnostics by remember {
        mutableStateOf(ReminderDiagnostics.snapshot(context))
    }
    var systemStatus by remember {
        mutableStateOf(ReminderSystemSettings.status(context))
    }
    var eventLogs by remember {
        mutableStateOf(ReminderEventLog.entries(context, 20))
    }
    var editorReminder by remember { mutableStateOf<Reminder?>(null) }
    var editorVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }

    fun refresh(reconcile: Boolean = false) {
        ReminderNotifications.ensureChannel(context)
        val currentReminders = repository.getAll()
        if (reconcile) {
            ReminderScheduler.reconcile(
                context,
                currentReminders,
                ReminderScheduleSource.APP_FOREGROUND_RECONCILE
            )
        }
        reminders = currentReminders
        reliabilityStatus = ReminderNotifications.status(context)
        diagnostics = ReminderDiagnostics.snapshot(context)
        systemStatus = ReminderSystemSettings.status(context)
        eventLogs = ReminderEventLog.entries(context, 20)
    }

    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refresh(reconcile = true)
    }

    LaunchedEffect(Unit) {
        repository.createTestReminderIfNeeded()
        refresh(reconcile = true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh(reconcile = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(context) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            diagnostics = ReminderDiagnostics.snapshot(context)
        }
        ReminderDiagnostics.registerChangeListener(context, listener)
        onDispose { ReminderDiagnostics.unregisterChangeListener(context, listener) }
    }

    fun saveReminder(reminder: Reminder) {
        val preparedReminder = ReminderScheduler.prepareForSave(reminder)
        repository.save(preparedReminder)
        ReminderEventLog.append(
            context,
            ReminderLogType.USER_ACTION,
            "保存提醒；enabled=${preparedReminder.enabled}；repeat=${preparedReminder.repeatMode.name}",
            preparedReminder.id
        )
        if (preparedReminder.enabled) {
            ReminderScheduler.schedule(context, preparedReminder)
        } else {
            ReminderScheduler.cancel(context, preparedReminder)
            NotificationManagerCompat.from(context).cancel(preparedReminder.notificationId)
        }
        refresh()
        editorVisible = false
        if (preparedReminder.enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        ReminderScheduler.cancel(context, reminder)
        NotificationManagerCompat.from(context).cancel(reminder.notificationId)
        repository.delete(reminder.id)
        ReminderEventLog.append(
            context,
            ReminderLogType.USER_ACTION,
            "删除提醒并撤销系统闹钟",
            reminder.id
        )
        refresh()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DaoDianLaColors.background) {
        if (settingsVisible) {
            ReminderSettingsScreen(
                status = systemStatus,
                logs = eventLogs,
                onBack = { settingsVisible = false },
                onRefresh = { refresh() },
                onOpenNotificationSettings = {
                    ReminderSystemSettings.openNotificationSettings(context)
                },
                onOpenExactAlarmSettings = {
                    ReminderSystemSettings.openExactAlarmSettings(context)
                },
                onOpenAutostartSettings = {
                    ReminderSystemSettings.openAutostartSettings(context)
                },
                onOpenBatterySettings = {
                    ReminderSystemSettings.openBatterySettings(context)
                },
                onManualCheckChanged = { check, confirmed ->
                    ReminderSystemSettings.setManualCheck(context, check, confirmed)
                    refresh()
                },
                onCopyLogs = {
                    ReminderSystemSettings.copyDiagnosticReport(context)
                },
                onShareLogs = {
                    ReminderSystemSettings.shareDiagnosticReport(context)
                }
            )
        } else if (editorVisible) {
            ReminderEditorScreen(
                initialReminder = editorReminder,
                onBack = { editorVisible = false },
                onSave = ::saveReminder
            )
        } else {
            HomeScreen(
                reminders = reminders,
                reliabilityStatus = reliabilityStatus,
                diagnostics = diagnostics,
                onOpenSettings = {
                    refresh()
                    settingsVisible = true
                },
                onAdd = {
                    editorReminder = null
                    editorVisible = true
                },
                onEdit = { reminder ->
                    editorReminder = reminder
                    editorVisible = true
                },
                onDelete = ::deleteReminder,
                onOpenNotificationSettings = {
                    ReminderSystemSettings.openNotificationSettings(context)
                },
                onOpenExactAlarmSettings = {
                    ReminderSystemSettings.openExactAlarmSettings(context)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    reminders: List<Reminder>,
    reliabilityStatus: ReminderReliabilityStatus,
    diagnostics: ReminderDiagnosticsSnapshot,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    val enabledReminders = reminders.filter { it.enabled }
    val nextReminder = enabledReminders.minByOrNull {
        ReminderScheduler.nextTriggerMillis(it, System.currentTimeMillis())
    }
    val today = LocalDate.now()

    Scaffold(
        containerColor = DaoDianLaColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "到点啦",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = DaoDianLaColors.ink
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 18.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DaoDianLaColors.blueTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = DaoDianLaColors.blue,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "提醒可靠性设置",
                            tint = DaoDianLaColors.blue
                        )
                    }
                    Text(
                        text = "${enabledReminders.size} 条",
                        modifier = Modifier.padding(end = 20.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = DaoDianLaColors.muted
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DaoDianLaColors.background
                )
            )
        },
        bottomBar = {
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DaoDianLaColors.blue,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("新建提醒", fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = today.format(DateTimeFormatter.ofPattern("M月d日")),
                        style = MaterialTheme.typography.labelLarge,
                        color = DaoDianLaColors.blue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA),
                        style = MaterialTheme.typography.headlineMedium,
                        color = DaoDianLaColors.ink,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item {
                NextReminderCard(nextReminder)
            }

            item {
                ReliabilityCard(
                    status = reliabilityStatus,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings
                )
            }


            item {
                ReminderDiagnosticsCard(diagnostics)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "提醒清单",
                        style = MaterialTheme.typography.titleMedium,
                        color = DaoDianLaColors.ink,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "滑除通知即可完成",
                        style = MaterialTheme.typography.labelMedium,
                        color = DaoDianLaColors.muted
                    )
                }
            }

            if (reminders.isEmpty()) {
                item { EmptyState(onAdd) }
            } else {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onClick = { onEdit(reminder) },
                        onDelete = { onDelete(reminder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderDiagnosticsCard(diagnostics: ReminderDiagnosticsSnapshot) {
    val scheduleFailed = diagnostics.scheduleError != null
    val title = when {
        scheduleFailed -> "最近一次闹钟提交失败"
        diagnostics.scheduleSubmittedAt == null -> "尚无闹钟提交记录"
        diagnostics.scheduleExact -> "已提交系统精确闹钟"
        else -> "已提交普通系统闹钟"
    }
    val scheduleSourceLabel = when (diagnostics.scheduleSource) {
        ReminderScheduleSource.USER_SAVE -> "保存提交"
        ReminderScheduleSource.APP_FOREGROUND_RECONCILE -> "打开 App 核对"
        ReminderScheduleSource.SYSTEM_RESTORE -> "系统恢复核对"
        ReminderScheduleSource.REPEAT_NEXT -> "重复提醒续排"
        null -> null
    }
    val scheduleDescription = when {
        scheduleFailed -> diagnostics.scheduleError.orEmpty()
        diagnostics.scheduleSubmittedAt != null && diagnostics.scheduledTriggerAt != null -> {
            val targetLabel = if (
                diagnostics.requestedTriggerAt != null &&
                diagnostics.requestedTriggerAt != diagnostics.scheduledTriggerAt
            ) {
                "原定 ${formatDateTimeWithSeconds(diagnostics.requestedTriggerAt)} · 补发目标 ${formatDateTimeWithSeconds(diagnostics.scheduledTriggerAt)}"
            } else {
                "目标 ${formatDateTimeWithSeconds(diagnostics.scheduledTriggerAt)}"
            }
            listOfNotNull(
                scheduleSourceLabel,
                targetLabel,
                "提交于 ${formatClockWithSeconds(diagnostics.scheduleSubmittedAt)}"
            ).joinToString(" · ")
        }
        else -> "保存一条提醒后，这里会显示系统接收状态"
    }
    val latestDeliveryNotificationFailed = diagnostics.deliveredAt?.let { deliveredAt ->
        diagnostics.notificationFailedAt?.let { it >= deliveredAt } == true &&
            diagnostics.notificationError != null
    } ?: false
    val deliveryDescription = diagnostics.deliveredAt?.let { deliveredAt ->
        val sourceLabel = when (diagnostics.deliverySource) {
            ReminderDeliverySource.APP_OPEN_CATCH_UP -> "打开 App 后补发"
            ReminderDeliverySource.SYSTEM_RESTORE_CATCH_UP -> "系统恢复后补发"
            ReminderDeliverySource.SYSTEM_ALARM -> "系统闹钟触发"
            null -> "历史触发（来源未记录）"
        }
        val notificationLabel = when {
            latestDeliveryNotificationFailed -> " · 通知失败：${diagnostics.notificationError}"
            else -> diagnostics.notificationPostedAt
                ?.takeIf { it >= deliveredAt }
                ?.let { " · 通知已发布 ${formatClockWithSeconds(it)}" }
                .orEmpty()
        }
        "最近投递：$sourceLabel · ${formatDateTimeWithSeconds(deliveredAt)}$notificationLabel"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DaoDianLaColors.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (scheduleFailed) DaoDianLaColors.warningTint
                        else DaoDianLaColors.blueTint
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = if (scheduleFailed) DaoDianLaColors.warning else DaoDianLaColors.blue,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = DaoDianLaColors.ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    scheduleDescription,
                    color = DaoDianLaColors.muted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (deliveryDescription != null) {
                    Spacer(Modifier.height(7.dp))
                    Text(
                        deliveryDescription,
                        color = if (latestDeliveryNotificationFailed) {
                            DaoDianLaColors.warning
                        } else {
                            DaoDianLaColors.blue
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ReliabilityCard(
    status: ReminderReliabilityStatus,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit
) {
    val cardColor = if (status.healthy) DaoDianLaColors.blueTint else DaoDianLaColors.warningTint
    val accentColor = if (status.healthy) DaoDianLaColors.blue else DaoDianLaColors.warning
    val title = when {
        !status.notificationsAllowed -> "需要开启通知权限"
        !status.exactAlarmAllowed -> "需要开启精确闹钟"
        else -> "提醒通道工作正常"
    }
    val description = when {
        !status.notificationsAllowed -> "闹钟会触发，但通知无法显示"
        !status.exactAlarmAllowed -> "否则锁屏时可能延迟提醒"
        else -> "锁屏后仍由系统准点唤醒"
    }
    val repairAction = when {
        !status.notificationsAllowed -> onOpenNotificationSettings
        !status.exactAlarmAllowed -> onOpenExactAlarmSettings
        else -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = DaoDianLaColors.ink,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    color = DaoDianLaColors.muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (repairAction != null) {
                Text(
                    text = "去开启",
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = repairAction)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun NextReminderCard(reminder: Reminder?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = DaoDianLaColors.hero),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (reminder == null) {
            Row(
                modifier = Modifier.padding(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DaoDianLaColors.inkSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        tint = DaoDianLaColors.blue,
                        modifier = Modifier.size(25.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("还没有提醒", color = DaoDianLaColors.ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(3.dp))
                    Text("新建一条，让重要的事准时出现", color = DaoDianLaColors.muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            val nextAt = ReminderScheduler.nextTriggerMillis(reminder, System.currentTimeMillis())
            Row(
                modifier = Modifier.padding(22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("下一条提醒", color = DaoDianLaColors.blue, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = formatDateTime(nextAt),
                        color = DaoDianLaColors.ink,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(reminder.title, color = DaoDianLaColors.muted, style = MaterialTheme.typography.bodyMedium)
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DaoDianLaColors.blue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DaoDianLaColors.line)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = DaoDianLaColors.blue, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(10.dp))
            Text("清单是空的", color = DaoDianLaColors.ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("比如：明日方舟登录、每日下班打卡", color = DaoDianLaColors.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(14.dp)) { Text("添加第一条") }
        }
    }
}

@Composable
private fun ReminderCard(reminder: Reminder, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (reminder.enabled) Color.White else DaoDianLaColors.disabled
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DaoDianLaColors.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(66.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (reminder.enabled) DaoDianLaColors.amber else DaoDianLaColors.muted.copy(alpha = 0.35f))
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "%02d:%02d".format(reminder.hour, reminder.minute),
                    color = DaoDianLaColors.ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
                Spacer(Modifier.height(3.dp))
                Text(reminder.title, color = DaoDianLaColors.ink, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallMeta(Icons.Outlined.Repeat, repeatLabel(reminder.repeatMode))
                    SmallMeta(Icons.Outlined.AccessTime, "持续 ${reminder.durationHours} 小时")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除提醒", tint = DaoDianLaColors.muted)
            }
        }
    }
}

@Composable
private fun SmallMeta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = DaoDianLaColors.muted)
        Text(text, color = DaoDianLaColors.muted, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditorScreen(
    initialReminder: Reminder?,
    onBack: () -> Unit,
    onSave: (Reminder) -> Unit
) {
    val context = LocalContext.current
    var title by remember(initialReminder?.id) { mutableStateOf(initialReminder?.title ?: "") }
    var timeMinutes by remember(initialReminder?.id) { mutableStateOf(initialReminder?.timeMinutes ?: defaultTestTimeMinutes()) }
    var durationHours by remember(initialReminder?.id) { mutableStateOf(initialReminder?.durationHours ?: 1) }
    var repeatMode by remember(initialReminder?.id) { mutableStateOf(initialReminder?.repeatMode ?: RepeatMode.ONCE) }
    var enabled by remember(initialReminder?.id) { mutableStateOf(initialReminder?.enabled ?: true) }
    var showTimePicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    if (showTimePicker) {
        androidx.compose.runtime.DisposableEffect(Unit) {
            val dialog = TimePickerDialog(
                context,
                { _, hour, minute ->
                    timeMinutes = hour * 60 + minute
                    showTimePicker = false
                },
                timeMinutes / 60,
                timeMinutes % 60,
                true
            )
            dialog.setOnDismissListener { showTimePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }

    Scaffold(
        containerColor = DaoDianLaColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (initialReminder == null) "新建提醒" else "编辑提醒",
                        color = DaoDianLaColors.ink,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = DaoDianLaColors.ink)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DaoDianLaColors.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "把提醒设好，时间到了它会出现在任务栏。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DaoDianLaColors.muted
                )
            }
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("提醒内容") },
                    placeholder = { Text("例如：明日方舟登录") },
                    isError = titleError,
                    supportingText = if (titleError) ({ Text("请写下要提醒你的事") }) else null,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            item {
                SettingCard {
                    Column {
                        Text("提醒时间", color = DaoDianLaColors.muted, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%02d:%02d".format(timeMinutes / 60, timeMinutes % 60),
                                color = DaoDianLaColors.ink,
                                fontSize = 38.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = { showTimePicker = true }, shape = RoundedCornerShape(13.dp)) {
                                Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("调整")
                            }
                        }
                    }
                }
            }
            item {
                SettingCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("持续时间", color = DaoDianLaColors.ink, fontWeight = FontWeight.Bold)
                                Text("通知最多保留多久", color = DaoDianLaColors.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text("${durationHours} 小时", color = DaoDianLaColors.blue, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = durationHours.toFloat(),
                            onValueChange = { durationHours = it.toInt().coerceIn(1, 24) },
                            valueRange = 1f..24f,
                            steps = 22,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = DaoDianLaColors.blue,
                                activeTrackColor = DaoDianLaColors.blue,
                                inactiveTrackColor = DaoDianLaColors.blueTint
                            )
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("1 小时", style = MaterialTheme.typography.labelSmall, color = DaoDianLaColors.muted)
                            Text("24 小时", style = MaterialTheme.typography.labelSmall, color = DaoDianLaColors.muted)
                        }
                    }
                }
            }
            item {
                SettingCard {
                    Column {
                        Text("重复频率", color = DaoDianLaColors.ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RepeatMode.values().forEach { mode ->
                                FilterChip(
                                    selected = repeatMode == mode,
                                    onClick = { repeatMode = mode },
                                    label = { Text(repeatLabel(mode)) },
                                    leadingIcon = if (repeatMode == mode) {
                                        { Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = DaoDianLaColors.blueTint,
                                        selectedLabelColor = DaoDianLaColors.blue,
                                        selectedLeadingIconColor = DaoDianLaColors.blue
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(
                            text = when (repeatMode) {
                                RepeatMode.ONCE -> "今天设置一个还没到的时间，提醒一次"
                                RepeatMode.DAILY -> "每天 ${formatTime(timeMinutes)} 提醒"
                                RepeatMode.WEEKDAYS -> "周一至周五 ${formatTime(timeMinutes)} 提醒"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = DaoDianLaColors.muted
                        )
                    }
                }
            }
            if (initialReminder != null) {
                item {
                    SettingCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("开启提醒", color = DaoDianLaColors.ink, fontWeight = FontWeight.Bold)
                                Text("关闭后不会在任务栏出现", color = DaoDianLaColors.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            titleError = true
                        } else {
                            onSave(
                                Reminder(
                                    id = initialReminder?.id ?: System.currentTimeMillis(),
                                    title = title.trim(),
                                    timeMinutes = timeMinutes,
                                    durationHours = durationHours,
                                    repeatMode = repeatMode,
                                    enabled = enabled
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DaoDianLaColors.blue)
                ) {
                    Text("保存提醒", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, DaoDianLaColors.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            content()
        }
    }
}

private fun repeatLabel(mode: RepeatMode): String = when (mode) {
    RepeatMode.ONCE -> "仅一次"
    RepeatMode.DAILY -> "每天"
    RepeatMode.WEEKDAYS -> "工作日"
}

private fun formatDateTime(timestamp: Long): String {
    val dateTime = java.time.LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(timestamp),
        java.time.ZoneId.systemDefault()
    )
    val today = LocalDate.now()
    val dayLabel = when (dateTime.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "${dateTime.monthValue}月${dateTime.dayOfMonth}日"
    }
    return "$dayLabel ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
}

private fun formatDateTimeWithSeconds(timestamp: Long): String {
    val dateTime = java.time.LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(timestamp),
        java.time.ZoneId.systemDefault()
    )
    val today = LocalDate.now()
    val dayLabel = when (dateTime.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "${dateTime.monthValue}月${dateTime.dayOfMonth}日"
    }
    return "$dayLabel ${dateTime.hour.toString().padStart(2, '0')}:" +
        "${dateTime.minute.toString().padStart(2, '0')}:" +
        dateTime.second.toString().padStart(2, '0')
}

private fun formatClockWithSeconds(timestamp: Long): String {
    val dateTime = java.time.LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(timestamp),
        java.time.ZoneId.systemDefault()
    )
    return "${dateTime.hour.toString().padStart(2, '0')}:" +
        "${dateTime.minute.toString().padStart(2, '0')}:" +
        dateTime.second.toString().padStart(2, '0')
}

private fun formatTime(timeMinutes: Int): String = "%02d:%02d".format(timeMinutes / 60, timeMinutes % 60)

private fun defaultTestTimeMinutes(): Int = Calendar.getInstance().apply {
    add(Calendar.MINUTE, 5)
}.let { calendar ->
    calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}
