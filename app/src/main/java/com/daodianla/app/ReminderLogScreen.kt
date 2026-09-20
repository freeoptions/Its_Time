package com.daodianla.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val LOG_PAGE_SIZE = 10

private enum class ReminderLogFilter(val label: String) {
    ALL("全部"), ISSUES("异常"), SCHEDULING("调度"), NOTIFICATIONS("通知")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderLogScreen(
    logs: List<ReminderLogEntry>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val listState = rememberLazyListState()
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var pageMenuExpanded by remember { mutableStateOf(false) }
    val filter = ReminderLogFilter.values()[filterIndex.coerceIn(0, ReminderLogFilter.values().lastIndex)]
    val filteredLogs = remember(logs, filter) { logs.filter { it.matches(filter) } }
    val pageCount = (filteredLogs.size + LOG_PAGE_SIZE - 1) / LOG_PAGE_SIZE
    val displayPage = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val pageLogs = filteredLogs.drop(displayPage * LOG_PAGE_SIZE).take(LOG_PAGE_SIZE)

    LaunchedEffect(filter) {
        currentPage = 0
        listState.scrollToItem(0)
    }
    LaunchedEffect(displayPage, filteredLogs.size) { listState.scrollToItem(0) }
    LaunchedEffect(pageCount) {
        currentPage = currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    }

    Scaffold(
        containerColor = DaoDianLaColors.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("诊断日志", color = DaoDianLaColors.ink, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = DaoDianLaColors.ink)
                    }
                },
                actions = {
                    IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "复制日志", tint = DaoDianLaColors.blue) }
                    IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, "分享日志", tint = DaoDianLaColors.blue) }
                    IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "刷新日志", tint = DaoDianLaColors.blue) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DaoDianLaColors.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LogFilterRow(filterIndex = filterIndex, onFilterChange = { filterIndex = it })
            LogPageContent(
                logs = logs,
                pageLogs = pageLogs,
                listState = listState
            )
            if (pageCount > 1) {
                ReminderLogPagination(
                    currentPage = displayPage,
                    pageCount = pageCount,
                    menuExpanded = pageMenuExpanded,
                    onMenuExpandedChange = { pageMenuExpanded = it },
                    onPageChange = { currentPage = it }
                )
            }
        }
    }
}

@Composable
private fun LogFilterRow(filterIndex: Int, onFilterChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ReminderLogFilter.values().forEachIndexed { index, item ->
            FilterChip(
                selected = filterIndex == index,
                onClick = { onFilterChange(index) },
                label = { Text(item.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DaoDianLaColors.blueTint,
                    selectedLabelColor = DaoDianLaColors.blue
                )
            )
        }
    }
}

@Composable
private fun ColumnScope.LogPageContent(
    logs: List<ReminderLogEntry>,
    pageLogs: List<ReminderLogEntry>,
    listState: LazyListState
) {
    if (pageLogs.isEmpty()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(if (logs.isEmpty()) "暂无诊断事件" else "当前筛选暂无记录", color = DaoDianLaColors.muted)
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(pageLogs, key = { index, entry -> "${entry.timestamp}-${index}" }) { _, entry ->
                ReminderLogItem(entry)
            }
        }
    }
}

@Composable
private fun ReminderLogPagination(
    currentPage: Int,
    pageCount: Int,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onPageChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onPageChange((currentPage - 1).coerceAtLeast(0)) }, enabled = currentPage > 0) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一页")
        }
        Box {
            Surface(
                modifier = Modifier.clickable { onMenuExpandedChange(true) },
                shape = RoundedCornerShape(12.dp),
                color = DaoDianLaColors.blueTint,
                border = BorderStroke(1.dp, DaoDianLaColors.line)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("第 ${currentPage + 1} / $pageCount 页", style = MaterialTheme.typography.labelMedium, color = DaoDianLaColors.blue)
                    Icon(Icons.Outlined.ExpandMore, contentDescription = "选择页码", tint = DaoDianLaColors.blue)
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { onMenuExpandedChange(false) },
                modifier = Modifier.height(280.dp)
            ) {
                repeat(pageCount) { page ->
                    DropdownMenuItem(
                        text = { Text("第 ${page + 1} 页", color = if (page == currentPage) DaoDianLaColors.blue else DaoDianLaColors.ink) },
                        leadingIcon = if (page == currentPage) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, tint = DaoDianLaColors.blue) }
                        } else null,
                        onClick = {
                            onPageChange(page)
                            onMenuExpandedChange(false)
                        }
                    )
                }
            }
        }
        IconButton(onClick = { onPageChange((currentPage + 1).coerceAtMost(pageCount - 1)) }, enabled = currentPage < pageCount - 1) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "下一页")
        }
    }
}

@Composable
private fun ReminderLogItem(entry: ReminderLogEntry) {
    val issue = entry.isIssue()
    val accent = if (issue) DaoDianLaColors.warning else DaoDianLaColors.blue
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, DaoDianLaColors.line),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.width(9.dp))
                Text(
                    entry.readableTitle(),
                    color = DaoDianLaColors.ink,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ReminderEventLog.formatTimestamp(entry.timestamp).substring(5, 16),
                    color = DaoDianLaColors.muted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                entry.readableDetails(),
                color = if (issue) DaoDianLaColors.warning else DaoDianLaColors.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun ReminderLogEntry.matches(filter: ReminderLogFilter): Boolean = when (filter) {
    ReminderLogFilter.ALL -> true
    ReminderLogFilter.ISSUES -> isIssue()
    ReminderLogFilter.SCHEDULING -> type in setOf(
        ReminderLogType.RECONCILE,
        ReminderLogType.SCHEDULED,
        ReminderLogType.SCHEDULE_FAILED,
        ReminderLogType.ALARM_FIRED
    )
    ReminderLogFilter.NOTIFICATIONS -> type in setOf(
        ReminderLogType.NOTIFICATION_POSTED,
        ReminderLogType.NOTIFICATION_FAILED
    )
}

internal fun ReminderLogEntry.isIssue(): Boolean = type in setOf(
    ReminderLogType.SCHEDULE_FAILED,
    ReminderLogType.NOTIFICATION_FAILED
) || (type == ReminderLogType.SYSTEM_EVENT && (details.contains("失败") || details.contains("异常")))

internal fun ReminderLogEntry.readableTitle(): String = when (type) {
    ReminderLogType.PROCESS_STARTED -> "应用进程启动"
    ReminderLogType.APP_FOREGROUND -> "进入应用"
    ReminderLogType.RECONCILE -> "重新核对提醒"
    ReminderLogType.SCHEDULED -> "已提交系统闹钟"
    ReminderLogType.SCHEDULE_FAILED -> "系统闹钟提交失败"
    ReminderLogType.ALARM_FIRED -> "系统闹钟已触发"
    ReminderLogType.NOTIFICATION_POSTED -> "通知已发布"
    ReminderLogType.NOTIFICATION_FAILED -> "通知发布失败"
    ReminderLogType.SYSTEM_EVENT -> "系统处理提醒"
    ReminderLogType.USER_ACTION -> "用户操作"
    ReminderLogType.SETTINGS_CHECK -> "设置检查"
}

internal fun ReminderLogEntry.readableDetails(): String = when (type) {
    ReminderLogType.PROCESS_STARTED -> "系统创建了到点啦进程，提醒可以继续由系统接管。"
    ReminderLogType.APP_FOREGROUND -> "你打开了应用，应用会重新核对已有提醒。"
    ReminderLogType.RECONCILE -> "应用已重新核对提醒并更新系统闹钟。"
    ReminderLogType.SCHEDULED -> if (details.contains("exact=true")) {
        "系统已接收精确唤醒提醒。"
    } else {
        "系统已接收提醒，但当前使用的是普通待机唤醒。"
    }
    ReminderLogType.SCHEDULE_FAILED -> "提醒没有成功交给系统：${details.substringAfter("error=", details).take(140)}"
    ReminderLogType.ALARM_FIRED -> "到点啦，系统已经进入提醒投递流程。"
    ReminderLogType.NOTIFICATION_POSTED -> "通知已交给系统显示。"
    ReminderLogType.NOTIFICATION_FAILED -> "通知没有显示：${details.substringAfter("error=", details).take(140)}"
    ReminderLogType.SYSTEM_EVENT -> when {
        details.contains("启动失败") -> "短时提醒服务启动失败，应用已尝试直接投递通知。"
        details.contains("接管提醒投递") -> "短时提醒服务已接管投递，并准备结束。"
        else -> "系统完成了一步提醒处理。"
    }
    ReminderLogType.USER_ACTION -> details.take(140)
    ReminderLogType.SETTINGS_CHECK -> "已检查提醒相关设置。"
}
