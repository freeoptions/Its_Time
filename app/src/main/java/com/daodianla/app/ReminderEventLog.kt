package com.daodianla.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ReminderLogType(val label: String) {
    PROCESS_STARTED("进程启动"),
    APP_FOREGROUND("进入前台"),
    RECONCILE("提醒对账"),
    SCHEDULED("闹钟提交"),
    SCHEDULE_FAILED("提交失败"),
    ALARM_FIRED("闹钟触发"),
    NOTIFICATION_POSTED("通知发布"),
    NOTIFICATION_FAILED("通知失败"),
    SYSTEM_EVENT("系统事件"),
    USER_ACTION("用户操作"),
    SETTINGS_CHECK("设置确认")
}

data class ReminderLogEntry(
    val timestamp: Long,
    val type: ReminderLogType,
    val reminderId: Long?,
    val details: String
)

/**
 * 本地滚动诊断日志。仅记录调度元数据，不记录提醒标题或其他用户内容。
 */
object ReminderEventLog {
    private const val PREFERENCES_NAME = "reminder_event_log"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 120
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    @Synchronized
    fun append(
        context: Context,
        type: ReminderLogType,
        details: String,
        reminderId: Long? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val existing = parseArray(preferences.getString(KEY_ENTRIES, null))
        existing.put(
            JSONObject().apply {
                put("timestamp", timestamp)
                put("type", type.name)
                reminderId?.let { put("reminderId", it) }
                put("details", details.take(500))
            }
        )

        val trimmed = JSONArray()
        val startIndex = (existing.length() - MAX_ENTRIES).coerceAtLeast(0)
        for (index in startIndex until existing.length()) {
            existing.optJSONObject(index)?.let { trimmed.put(it) }
        }
        preferences.edit().putString(KEY_ENTRIES, trimmed.toString()).commit()
    }

    fun entries(context: Context, limit: Int = 30): List<ReminderLogEntry> {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val array = parseArray(preferences.getString(KEY_ENTRIES, null))
        return buildList {
            for (index in array.length() - 1 downTo 0) {
                if (size >= limit.coerceAtLeast(0)) break
                array.optJSONObject(index)?.toEntry()?.let(::add)
            }
        }
    }

    fun formatTimestamp(timestamp: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
            .format(timestampFormatter)

    private fun parseArray(raw: String?): JSONArray = runCatching {
        JSONArray(raw ?: "[]")
    }.getOrDefault(JSONArray())

    private fun JSONObject.toEntry(): ReminderLogEntry? {
        val timestamp = optLong("timestamp", 0L).takeIf { it > 0L } ?: return null
        val type = runCatching {
            ReminderLogType.valueOf(optString("type"))
        }.getOrDefault(ReminderLogType.SYSTEM_EVENT)
        return ReminderLogEntry(
            timestamp = timestamp,
            type = type,
            reminderId = optLong("reminderId", 0L).takeIf { it > 0L },
            details = optString("details", "")
        )
    }
}
