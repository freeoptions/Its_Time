package com.daodianla.app

import android.content.Context
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.TimeUnit

data class HolidayCalendarSyncInfo(
    val targetYear: Int,
    val targetYearStatus: HolidayYearStatus,
    val lastCheckedAtMillis: Long?,
    val lastMessage: String?
)

data class HolidayCalendarCheckResult(
    val updated: Boolean,
    val message: String
)

object HolidayCalendarSync {
    const val DATA_URL =
        "https://raw.githubusercontent.com/freeoptions/Its_Time/main/calendar/holidays.json"

    private const val PREFERENCES_NAME = "holiday_calendar_sync"
    private const val LAST_ATTEMPT_KEY = "last_attempt_at"
    private const val LAST_MESSAGE_KEY = "last_message"
    private val MIN_CHECK_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24)

    fun info(context: Context): HolidayCalendarSyncInfo {
        val targetYear = LocalDate.now().year + 1
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return HolidayCalendarSyncInfo(
            targetYear = targetYear,
            targetYearStatus = ChineseWorkdayCalendar.statusForYear(targetYear),
            lastCheckedAtMillis = preferences.getLong(LAST_ATTEMPT_KEY, 0L).takeIf { it > 0L },
            lastMessage = preferences.getString(LAST_MESSAGE_KEY, null)
        )
    }

    fun check(context: Context): HolidayCalendarCheckResult {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAttempt = preferences.getLong(LAST_ATTEMPT_KEY, 0L)
        if (lastAttempt > 0L && now - lastAttempt < MIN_CHECK_INTERVAL_MILLIS) {
            val message = "最近已检查过，24 小时内不重复请求"
            rememberMessage(preferences, message)
            return HolidayCalendarCheckResult(updated = false, message = message)
        }

        preferences.edit().putLong(LAST_ATTEMPT_KEY, now).apply()
        val result = runCatching {
            val connection = (URL(DATA_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "DaoDianLa-CalendarSync/1.0")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("服务器返回 HTTP ${connection.responseCode}")
                }
                val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                if (payload.length > 512_000) {
                    throw IOException("节假日数据文件过大")
                }
                if (!ChineseWorkdayCalendar.applyRemotePayload(context, payload)) {
                    throw IOException("节假日数据格式无法识别")
                }
            } finally {
                connection.disconnect()
            }
            targetYearMessage()
        }.getOrElse { error ->
            "检查失败：${error.message ?: "网络不可用"}"
        }

        rememberMessage(preferences, result)
        return HolidayCalendarCheckResult(
            updated = result.startsWith("数据已更新"),
            message = result
        )
    }

    private fun targetYearMessage(): String {
        val targetYear = LocalDate.now().year + 1
        return when (ChineseWorkdayCalendar.statusForYear(targetYear)) {
            HolidayYearStatus.PUBLISHED -> "数据已更新，${targetYear} 年安排已发布"
            HolidayYearStatus.NOT_PUBLISHED -> "数据已更新，${targetYear} 年官方安排暂未发布"
            HolidayYearStatus.LOCAL_FALLBACK -> "数据已更新，${targetYear} 年暂按周一至周五判断"
        }
    }

    private fun rememberMessage(
        preferences: android.content.SharedPreferences,
        message: String
    ) {
        preferences.edit().putString(LAST_MESSAGE_KEY, message).apply()
    }
}
