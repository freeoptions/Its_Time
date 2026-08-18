package com.daodianla.app

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            )
        ) return

        ReminderEventLog.append(
            context,
            ReminderLogType.SYSTEM_EVENT,
            "收到系统广播：${intent.action ?: "UNKNOWN"}"
        )

        ReminderScheduler.reconcile(
            context,
            ReminderRepository(context).getAll(),
            ReminderScheduleSource.SYSTEM_RESTORE
        )
    }
}
