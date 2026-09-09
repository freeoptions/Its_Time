package com.daodianla.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * 精确闹钟触发后的短生命周期前台服务。
 * 仅在到点投递期间存在，完成通知发布和下一次调度后立即退出，不用于常驻保活。
 */
class ReminderTriggerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ensureChannel()
            startAsForeground()

            val reminderId = intent?.getLongExtra(
                ReminderScheduler.EXTRA_REMINDER_ID,
                Long.MIN_VALUE
            ) ?: Long.MIN_VALUE
            if (intent?.action == ReminderScheduler.ACTION_FIRE && reminderId != Long.MIN_VALUE) {
                val deliverySource = intent.getStringExtra(ReminderScheduler.EXTRA_DELIVERY_SOURCE)
                    ?.let { value ->
                        runCatching { ReminderDeliverySource.valueOf(value) }.getOrNull()
                    }
                    ?: ReminderDeliverySource.SYSTEM_ALARM
                ReminderAlarmReceiver().deliverReminder(this, reminderId, deliverySource)
            } else {
                ReminderEventLog.append(
                    this,
                    ReminderLogType.SYSTEM_EVENT,
                    "触发服务收到无效请求"
                )
            }
        } catch (error: Throwable) {
            ReminderEventLog.append(
                this,
                ReminderLogType.NOTIFICATION_FAILED,
                "触发服务执行失败；error=${error.message ?: "未知错误"}"
            )
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_trigger_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_trigger_channel_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_trigger_title))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "reminder_trigger_execution"
        const val NOTIFICATION_ID = 987_654_321
    }
}
