package com.daodianla.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {
    const val ACTION_FIRE = "com.daodianla.app.action.FIRE_REMINDER"
    const val ACTION_MARK_DONE = "com.daodianla.app.action.MARK_REMINDER_DONE"
    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_DELIVERY_SOURCE = "extra_delivery_source"
    private const val SHOW_REQUEST_OFFSET = 200_000
    private const val MISSED_REMINDER_DELAY_MILLIS = 1_000L

    fun schedule(
        context: Context,
        reminder: Reminder,
        fromMillis: Long = System.currentTimeMillis(),
        source: ReminderScheduleSource = ReminderScheduleSource.USER_SAVE
    ) {
        if (!reminder.enabled) {
            cancel(context, reminder)
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val requestedTriggerAtMillis = nextTriggerMillis(reminder, fromMillis)
        val triggerAtMillis = scheduleTriggerMillis(reminder, fromMillis)
        val deliverySource = deliverySource(reminder, requestedTriggerAtMillis, fromMillis, source)
        val pendingIntent = alarmPendingIntent(context, reminder, deliverySource)

        if (canScheduleExactAlarms(context)) {
            val exactResult = runCatching {
                // 注册为系统闹钟，应用进程不需要常驻，Doze 期间也能唤醒设备。
                val showIntent = PendingIntent.getActivity(
                    context,
                    reminder.notificationId + SHOW_REQUEST_OFFSET,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
                    pendingIntent
                )
            }
            if (exactResult.isSuccess) {
                ReminderDiagnostics.recordScheduled(
                    context,
                    reminder.id,
                    requestedTriggerAtMillis,
                    triggerAtMillis,
                    exact = true,
                    source = source
                )
                ReminderEventLog.append(
                    context,
                    ReminderLogType.SCHEDULED,
                    "exact=true；source=${source.name}；requested=$requestedTriggerAtMillis；target=$triggerAtMillis",
                    reminder.id
                )
                return
            }
            // 权限在调度瞬间被撤销时，仍保留一个可能稍有延迟的兜底提醒。
            scheduleInexactSafely(
                context,
                alarmManager,
                reminder.id,
                requestedTriggerAtMillis,
                triggerAtMillis,
                pendingIntent,
                source
            )
        } else {
            scheduleInexactSafely(
                context,
                alarmManager,
                reminder.id,
                requestedTriggerAtMillis,
                triggerAtMillis,
                pendingIntent,
                source
            )
        }
    }

    /**
     * 在应用回到前台、系统重启或应用升级后重新核对全部提醒。
     * 同一个 PendingIntent 会被系统替换，不会产生重复闹钟。
     */
    fun reconcile(
        context: Context,
        reminders: List<Reminder>,
        source: ReminderScheduleSource
    ) {
        ReminderEventLog.append(
            context,
            ReminderLogType.RECONCILE,
            "source=${source.name}；total=${reminders.size}；enabled=${reminders.count { it.enabled }}"
        )
        reminders.forEach { reminder ->
            if (reminder.enabled) schedule(context, reminder, source = source) else cancel(context, reminder)
        }
    }

    /** 保存单次提醒时固定绝对时间，避免进程重建后被错误推迟到下一天。 */
    fun prepareForSave(reminder: Reminder, fromMillis: Long = System.currentTimeMillis()): Reminder {
        return if (reminder.repeatMode == RepeatMode.ONCE && reminder.enabled) {
            val triggerAt = nextWallClockOccurrence(reminder, fromMillis)
            reminder.copy(triggerAtMillis = triggerAt)
        } else {
            reminder.copy(triggerAtMillis = null)
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun scheduleInexact(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun scheduleInexactSafely(
        context: Context,
        alarmManager: AlarmManager,
        reminderId: Long,
        requestedTriggerAtMillis: Long,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        source: ReminderScheduleSource
    ) {
        runCatching {
            scheduleInexact(alarmManager, triggerAtMillis, pendingIntent)
        }.onSuccess {
            ReminderDiagnostics.recordScheduled(
                context,
                reminderId,
                requestedTriggerAtMillis,
                triggerAtMillis,
                exact = false,
                source = source
            )
            ReminderEventLog.append(
                context,
                ReminderLogType.SCHEDULED,
                "exact=false；source=${source.name}；requested=$requestedTriggerAtMillis；target=$triggerAtMillis",
                reminderId
            )
        }.onFailure {
            ReminderDiagnostics.recordScheduleFailure(
                context,
                reminderId,
                it.message ?: "系统闹钟登记失败"
            )
            ReminderEventLog.append(
                context,
                ReminderLogType.SCHEDULE_FAILED,
                "source=${source.name}；target=$triggerAtMillis；error=${it.message ?: "未知错误"}",
                reminderId
            )
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(alarmPendingIntent(context, reminder))
    }

    fun nextTriggerMillis(reminder: Reminder, fromMillis: Long): Long {
        if (reminder.repeatMode == RepeatMode.ONCE) {
            reminder.triggerAtMillis?.let { return it }
        }

        return nextWallClockOccurrence(reminder, fromMillis)
    }

    internal fun scheduleTriggerMillis(reminder: Reminder, fromMillis: Long): Long {
        val expected = nextTriggerMillis(reminder, fromMillis)
        return if (reminder.repeatMode == RepeatMode.ONCE && expected <= fromMillis) {
            fromMillis + MISSED_REMINDER_DELAY_MILLIS
        } else {
            expected
        }
    }

    internal fun deliverySource(
        reminder: Reminder,
        requestedTriggerAtMillis: Long,
        fromMillis: Long,
        source: ReminderScheduleSource
    ): ReminderDeliverySource {
        val isCatchUp = reminder.repeatMode == RepeatMode.ONCE &&
            requestedTriggerAtMillis <= fromMillis
        return when {
            isCatchUp && source == ReminderScheduleSource.APP_FOREGROUND_RECONCILE ->
                ReminderDeliverySource.APP_OPEN_CATCH_UP
            isCatchUp -> ReminderDeliverySource.SYSTEM_RESTORE_CATCH_UP
            else -> ReminderDeliverySource.SYSTEM_ALARM
        }
    }

    private fun nextWallClockOccurrence(reminder: Reminder, fromMillis: Long): Long {
        val candidate = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (candidate.timeInMillis <= fromMillis) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }

        if (reminder.repeatMode == RepeatMode.WEEKDAYS) {
            while (!isWeekday(candidate)) {
                candidate.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return candidate.timeInMillis
    }

    private fun isWeekday(calendar: Calendar): Boolean =
        calendar.get(Calendar.DAY_OF_WEEK) !in setOf(Calendar.SATURDAY, Calendar.SUNDAY)

    private fun alarmPendingIntent(
        context: Context,
        reminder: Reminder,
        deliverySource: ReminderDeliverySource = ReminderDeliverySource.SYSTEM_ALARM
    ): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_DELIVERY_SOURCE, deliverySource.name)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

}
