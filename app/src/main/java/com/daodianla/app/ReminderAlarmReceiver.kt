package com.daodianla.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, Long.MIN_VALUE)
        if (reminderId == Long.MIN_VALUE) return

        val repository = ReminderRepository(context)
        val reminder = repository.getById(reminderId) ?: return
        if (!reminder.enabled && intent.action == ReminderScheduler.ACTION_FIRE) return

        when (intent.action) {
            ReminderScheduler.ACTION_FIRE -> {
                val deliverySource = intent.getStringExtra(ReminderScheduler.EXTRA_DELIVERY_SOURCE)
                    ?.let { value ->
                        runCatching { ReminderDeliverySource.valueOf(value) }.getOrNull()
                    }
                    ?: ReminderDeliverySource.SYSTEM_ALARM
                ReminderDiagnostics.recordFired(context, reminder.id, deliverySource)
                ReminderEventLog.append(
                    context,
                    ReminderLogType.ALARM_FIRED,
                    "source=${deliverySource.name}；stage=RECEIVER；expected=${reminder.triggerAtMillis ?: reminder.timeMinutes}",
                    reminder.id
                )

                val serviceIntent = Intent(context, ReminderTriggerService::class.java).apply {
                    action = ReminderScheduler.ACTION_FIRE
                    putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
                    putExtra(ReminderScheduler.EXTRA_DELIVERY_SOURCE, deliverySource.name)
                }
                runCatching {
                    ContextCompat.startForegroundService(context, serviceIntent)
                }.onFailure { error ->
                    // 精确闹钟通常允许从后台启动 FGS；厂商实现异常时仍直接投递，避免整条链路丢失。
                    ReminderEventLog.append(
                        context,
                        ReminderLogType.SYSTEM_EVENT,
                        "触发服务启动失败，改为广播内直接投递；error=${error.message ?: "未知错误"}",
                        reminder.id
                    )
                    fireReminder(context, repository, reminder, deliverySource)
                }
            }
            ReminderScheduler.ACTION_MARK_DONE -> markDone(context, repository, reminder)
        }
    }

    internal fun deliverReminder(
        context: Context,
        reminderId: Long,
        deliverySource: ReminderDeliverySource
    ) {
        val repository = ReminderRepository(context)
        val reminder = repository.getById(reminderId)
        if (reminder == null) {
            ReminderEventLog.append(
                context,
                ReminderLogType.NOTIFICATION_FAILED,
                "触发后未找到提醒数据",
                reminderId
            )
            return
        }
        fireReminder(context, repository, reminder, deliverySource)
    }

    private fun fireReminder(
        context: Context,
        repository: ReminderRepository,
        reminder: Reminder,
        deliverySource: ReminderDeliverySource
    ) {
        // 先持久化完成状态或下一次闹钟，再显示通知；即使通知层异常也不会破坏调度链。
        if (reminder.repeatMode == RepeatMode.ONCE) {
            repository.save(reminder.copy(enabled = false))
        } else {
            ReminderScheduler.schedule(
                context,
                reminder,
                System.currentTimeMillis() + 60_000L,
                ReminderScheduleSource.REPEAT_NEXT
            )
        }
        ReminderEventLog.append(
            context,
            ReminderLogType.SYSTEM_EVENT,
            "短时触发服务接管提醒投递；source=${deliverySource.name}",
            reminder.id
        )
        showReminder(context, reminder)
    }

    private fun showReminder(context: Context, reminder: Reminder) {
        ReminderNotifications.ensureChannel(context)
        val openIntent = PendingIntent.getActivity(
            context,
            reminder.notificationId,
            Intent(context, MainActivity::class.java).apply {
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val doneIntent = PendingIntent.getBroadcast(
            context,
            reminder.notificationId + DELETE_REQUEST_OFFSET,
            Intent(context, ReminderAlarmReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_MARK_DONE
                putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.app_icon))
            .setContentTitle(reminder.title)
            .setContentText("完成后向左滑除 · ${formatTime(reminder.timeMinutes)}")
            .setSubText("到点啦")
            .setContentIntent(openIntent)
            .setDeleteIntent(doneIntent)
            .setAutoCancel(false)
            .setOngoing(false)
            .setTimeoutAfter(reminder.durationHours * 60L * 60L * 1000L)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build()

        if (!ReminderNotifications.status(context).notificationsAllowed) {
            ReminderDiagnostics.recordNotificationFailure(context, reminder.id, "通知权限或提醒渠道未开启")
            ReminderEventLog.append(
                context,
                ReminderLogType.NOTIFICATION_FAILED,
                "通知权限或提醒渠道未开启",
                reminder.id
            )
            return
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(reminder.notificationId, notification)
        }.onSuccess {
            ReminderDiagnostics.recordNotificationPosted(context, reminder.id)
            ReminderEventLog.append(
                context,
                ReminderLogType.NOTIFICATION_POSTED,
                "通知已提交给系统；notificationId=${reminder.notificationId}",
                reminder.id
            )
        }.onFailure {
            ReminderDiagnostics.recordNotificationFailure(context, reminder.id, it.message ?: "通知发送失败")
            ReminderEventLog.append(
                context,
                ReminderLogType.NOTIFICATION_FAILED,
                "error=${it.message ?: "通知发送失败"}",
                reminder.id
            )
        }
    }

    private fun markDone(context: Context, repository: ReminderRepository, reminder: Reminder) {
        NotificationManagerCompat.from(context).cancel(reminder.notificationId)
        ReminderEventLog.append(
            context,
            ReminderLogType.USER_ACTION,
            "从通知栏划除并标记完成",
            reminder.id
        )
        if (reminder.repeatMode == RepeatMode.ONCE) {
            repository.save(reminder.copy(enabled = false))
        } else {
            ReminderScheduler.schedule(
                context,
                reminder,
                System.currentTimeMillis() + 1_000L,
                ReminderScheduleSource.REPEAT_NEXT
            )
        }
    }

    private fun formatTime(timeMinutes: Int): String =
        "%02d:%02d".format(timeMinutes / 60, timeMinutes % 60)

    private companion object {
        const val DELETE_REQUEST_OFFSET = 100_000
    }
}
