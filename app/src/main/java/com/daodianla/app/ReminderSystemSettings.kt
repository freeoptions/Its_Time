package com.daodianla.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

enum class ManualReliabilityCheck {
    AUTOSTART,
    UNRESTRICTED_BATTERY,
    RECENTS_LOCKED
}

data class ReminderSystemStatus(
    val reliability: ReminderReliabilityStatus,
    val batteryOptimizationIgnored: Boolean,
    val autostartConfirmed: Boolean,
    val unrestrictedBatteryConfirmed: Boolean,
    val recentsLockedConfirmed: Boolean
) {
    val automaticReadyCount: Int
        get() = listOf(reliability.notificationsAllowed, reliability.exactAlarmAllowed).count { it }

    val manualReadyCount: Int
        get() = listOf(
            autostartConfirmed,
            unrestrictedBatteryConfirmed,
            recentsLockedConfirmed
        ).count { it }

    val fullyReady: Boolean
        get() = automaticReadyCount == 2 && manualReadyCount == 3
}

object ReminderSystemSettings {
    private const val PREFERENCES_NAME = "reminder_reliability_settings"

    fun status(context: Context): ReminderSystemStatus {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val powerManager = context.getSystemService(PowerManager::class.java)
        return ReminderSystemStatus(
            reliability = ReminderNotifications.status(context),
            batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            autostartConfirmed = preferences.getBoolean(ManualReliabilityCheck.AUTOSTART.name, false),
            unrestrictedBatteryConfirmed = preferences.getBoolean(
                ManualReliabilityCheck.UNRESTRICTED_BATTERY.name,
                false
            ),
            recentsLockedConfirmed = preferences.getBoolean(
                ManualReliabilityCheck.RECENTS_LOCKED.name,
                false
            )
        )
    }

    fun setManualCheck(context: Context, check: ManualReliabilityCheck, confirmed: Boolean) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(check.name, confirmed)
            .commit()
        ReminderEventLog.append(
            context,
            ReminderLogType.SETTINGS_CHECK,
            "${check.name}=${if (confirmed) "CONFIRMED" else "NOT_CONFIRMED"}"
        )
    }

    fun openNotificationSettings(context: Context) {
        launchFirstAvailable(
            context,
            listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                },
                appDetailsIntent(context)
            )
        )
    }

    fun openExactAlarmSettings(context: Context) {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
            add(appDetailsIntent(context))
        }
        launchFirstAvailable(context, candidates)
    }

    fun openAutostartSettings(context: Context) {
        launchFirstAvailable(
            context,
            listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                ),
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                ).putExtra("extra_pkgname", context.packageName),
                appDetailsIntent(context)
            )
        )
    }

    fun openBatterySettings(context: Context) {
        launchFirstAvailable(
            context,
            listOf(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                ).apply {
                    putExtra("package_name", context.packageName)
                    putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
                },
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                appDetailsIntent(context)
            )
        )
    }

    fun diagnosticReport(context: Context): String {
        val status = status(context)
        val diagnostics = ReminderDiagnostics.snapshot(context)
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        return buildString {
            appendLine("到点啦诊断日志")
            appendLine("生成时间：${ReminderEventLog.formatTimestamp(System.currentTimeMillis())}")
            appendLine("应用版本：${packageInfo.versionName} ($versionCode)")
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android：${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("[可靠性状态]")
            appendLine("通知权限与渠道：${yesNo(status.reliability.notificationsAllowed)}")
            appendLine("精确闹钟：${yesNo(status.reliability.exactAlarmAllowed)}")
            appendLine("Android 电池优化豁免：${yesNo(status.batteryOptimizationIgnored)}")
            appendLine("自启动人工确认：${yesNo(status.autostartConfirmed)}")
            appendLine("无限制电量人工确认：${yesNo(status.unrestrictedBatteryConfirmed)}")
            appendLine("最近任务上锁人工确认：${yesNo(status.recentsLockedConfirmed)}")
            appendLine()
            appendLine("[最近状态]")
            appendLine("调度提交：${formatNullableTime(diagnostics.scheduleSubmittedAt)}")
            appendLine("原定触发：${formatNullableTime(diagnostics.requestedTriggerAt)}")
            appendLine("系统目标：${formatNullableTime(diagnostics.scheduledTriggerAt)}")
            appendLine("精确调度：${yesNo(diagnostics.scheduleExact)}")
            appendLine("调度来源：${diagnostics.scheduleSource ?: "无"}")
            appendLine("调度错误：${diagnostics.scheduleError ?: "无"}")
            appendLine("实际触发：${formatNullableTime(diagnostics.deliveredAt)}")
            appendLine("触发来源：${diagnostics.deliverySource ?: "无"}")
            appendLine("通知发布：${formatNullableTime(diagnostics.notificationPostedAt)}")
            appendLine("通知失败：${formatNullableTime(diagnostics.notificationFailedAt)}")
            appendLine("通知错误：${diagnostics.notificationError ?: "无"}")
            appendLine()
            appendLine("[事件日志，最新在前]")
            ReminderEventLog.entries(context, 80).forEach { entry ->
                append(ReminderEventLog.formatTimestamp(entry.timestamp))
                append(" | ${entry.type.name}")
                entry.reminderId?.let { append(" | reminderId=$it") }
                appendLine(" | ${entry.details}")
            }
        }
    }

    fun copyDiagnosticReport(context: Context) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText("到点啦诊断日志", diagnosticReport(context))
        )
    }

    fun shareDiagnosticReport(context: Context) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "到点啦诊断日志")
                    putExtra(Intent.EXTRA_TEXT, diagnosticReport(context))
                },
                "分享诊断日志"
            )
        )
    }

    private fun launchFirstAvailable(context: Context, intents: List<Intent>) {
        for (candidate in intents + appDetailsIntent(context)) {
            val intent = Intent(candidate)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    private fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )

    private fun yesNo(value: Boolean): String = if (value) "是" else "否"

    private fun formatNullableTime(timestamp: Long?): String =
        timestamp?.let(ReminderEventLog::formatTimestamp) ?: "无"
}
