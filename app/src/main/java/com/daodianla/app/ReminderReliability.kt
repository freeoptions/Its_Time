package com.daodianla.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class ReminderReliabilityStatus(
    val exactAlarmAllowed: Boolean,
    val notificationsAllowed: Boolean
) {
    val healthy: Boolean get() = exactAlarmAllowed && notificationsAllowed
}

enum class ReminderScheduleSource {
    USER_SAVE,
    APP_FOREGROUND_RECONCILE,
    SYSTEM_RESTORE,
    REPEAT_NEXT
}

enum class ReminderDeliverySource {
    SYSTEM_ALARM,
    APP_OPEN_CATCH_UP,
    SYSTEM_RESTORE_CATCH_UP
}

data class ReminderDiagnosticsSnapshot(
    val scheduleSubmittedAt: Long?,
    val requestedTriggerAt: Long?,
    val scheduledTriggerAt: Long?,
    val scheduleExact: Boolean,
    val scheduleSource: ReminderScheduleSource?,
    val scheduleError: String?,
    val deliveredAt: Long?,
    val deliverySource: ReminderDeliverySource?,
    val notificationPostedAt: Long?,
    val notificationFailedAt: Long?,
    val notificationError: String?
)

object ReminderNotifications {
    const val CHANNEL_ID = "reminder_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(true)
            }
        )
    }

    fun status(context: Context): ReminderReliabilityStatus {
        val runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        val appNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
            channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
        } else {
            true
        }
        return ReminderReliabilityStatus(
            exactAlarmAllowed = ReminderScheduler.canScheduleExactAlarms(context),
            notificationsAllowed = runtimePermissionGranted && appNotificationsEnabled && channelEnabled
        )
    }
}

/** 只保存最近一次调度/投递结果，便于出现漏提醒时在设备上定位。 */
object ReminderDiagnostics {
    private const val PREFERENCES_NAME = "reminder_diagnostics"

    fun recordScheduled(
        context: Context,
        reminderId: Long,
        requestedTriggerAtMillis: Long,
        scheduledTriggerAtMillis: Long,
        exact: Boolean,
        source: ReminderScheduleSource
    ) {
        edit(context) {
            putLong("last_reminder_id", reminderId)
            putLong("last_scheduled_at", System.currentTimeMillis())
            putLong("last_requested_trigger_at", requestedTriggerAtMillis)
            putLong("last_trigger_at", scheduledTriggerAtMillis)
            putBoolean("last_schedule_exact", exact)
            putString("last_schedule_source", source.name)
            remove("last_schedule_error")
        }
    }

    fun recordScheduleFailure(context: Context, reminderId: Long, reason: String) {
        edit(context) {
            putLong("last_reminder_id", reminderId)
            putLong("last_schedule_failure_at", System.currentTimeMillis())
            putString("last_schedule_error", reason.take(200))
        }
    }

    fun recordFired(
        context: Context,
        reminderId: Long,
        source: ReminderDeliverySource
    ) {
        edit(context) {
            putLong("last_reminder_id", reminderId)
            putLong("last_fired_at", System.currentTimeMillis())
            putString("last_delivery_source", source.name)
        }
    }

    fun recordNotificationPosted(context: Context, reminderId: Long) {
        edit(context) {
            putLong("last_reminder_id", reminderId)
            putLong("last_notification_at", System.currentTimeMillis())
            remove("last_notification_error")
        }
    }

    fun recordNotificationFailure(context: Context, reminderId: Long, reason: String) {
        edit(context) {
            putLong("last_reminder_id", reminderId)
            putLong("last_notification_failure_at", System.currentTimeMillis())
            putString("last_notification_error", reason.take(200))
        }
    }

    fun snapshot(context: Context): ReminderDiagnosticsSnapshot {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return ReminderDiagnosticsSnapshot(
            scheduleSubmittedAt = preferences.optionalLong("last_scheduled_at"),
            requestedTriggerAt = preferences.optionalLong("last_requested_trigger_at")
                ?: preferences.optionalLong("last_trigger_at"),
            scheduledTriggerAt = preferences.optionalLong("last_trigger_at"),
            scheduleExact = preferences.getBoolean("last_schedule_exact", false),
            scheduleSource = preferences.getString("last_schedule_source", null)
                ?.let { value -> runCatching { ReminderScheduleSource.valueOf(value) }.getOrNull() },
            scheduleError = preferences.getString("last_schedule_error", null),
            deliveredAt = preferences.optionalLong("last_fired_at"),
            deliverySource = preferences.getString("last_delivery_source", null)
                ?.let { value -> runCatching { ReminderDeliverySource.valueOf(value) }.getOrNull() },
            notificationPostedAt = preferences.optionalLong("last_notification_at"),
            notificationFailedAt = preferences.optionalLong("last_notification_failure_at"),
            notificationError = preferences.getString("last_notification_error", null)
        )
    }

    fun registerChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        preferences(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(
        context: Context,
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        preferences(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    private inline fun edit(
        context: Context,
        block: android.content.SharedPreferences.Editor.() -> Unit
    ) {
        preferences(context)
            .edit()
            .apply(block)
            .commit()
    }

    private fun android.content.SharedPreferences.optionalLong(key: String): Long? =
        if (contains(key)) getLong(key, 0L).takeIf { it > 0L } else null

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
