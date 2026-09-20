package com.daodianla.app

import android.content.Context
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

enum class HolidayYearStatus {
    PUBLISHED,
    NOT_PUBLISHED,
    LOCAL_FALLBACK
}

private data class HolidayYearData(
    val status: HolidayYearStatus,
    val holidays: Set<LocalDate> = emptySet(),
    val adjustedWorkdays: Set<LocalDate> = emptySet()
)

/**
 * 中国大陆法定节假日与调休工作日。
 *
 * 应用内置官方数据作为离线兜底，也可以加载仓库中的 holidays.json。Android/MIUI
 * 没有向普通应用公开统一的系统工作日接口，因此这里只接受可审计的日期数据。
 */
object ChineseWorkdayCalendar {
    private const val PREFERENCES_NAME = "holiday_calendar_sync"
    private const val PAYLOAD_KEY = "payload"

    private val builtInYears = mapOf(
        2026 to HolidayYearData(
            status = HolidayYearStatus.PUBLISHED,
            holidays = setOf(
                // 元旦：1 月 1 日至 3 日
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3),
                // 春节：2 月 15 日至 23 日
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 2, 17),
                LocalDate.of(2026, 2, 18),
                LocalDate.of(2026, 2, 19),
                LocalDate.of(2026, 2, 20),
                LocalDate.of(2026, 2, 21),
                LocalDate.of(2026, 2, 22),
                LocalDate.of(2026, 2, 23),
                // 清明节：4 月 4 日至 6 日
                LocalDate.of(2026, 4, 4),
                LocalDate.of(2026, 4, 5),
                LocalDate.of(2026, 4, 6),
                // 劳动节：5 月 1 日至 5 日
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 2),
                LocalDate.of(2026, 5, 3),
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 5),
                // 端午节：6 月 19 日至 21 日
                LocalDate.of(2026, 6, 19),
                LocalDate.of(2026, 6, 20),
                LocalDate.of(2026, 6, 21),
                // 中秋节：9 月 25 日至 27 日
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 9, 26),
                LocalDate.of(2026, 9, 27),
                // 国庆节：10 月 1 日至 7 日
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 3),
                LocalDate.of(2026, 10, 4),
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 7)
            ),
            adjustedWorkdays = setOf(
                LocalDate.of(2026, 1, 4),
                LocalDate.of(2026, 2, 14),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 5, 9),
                LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 10, 10)
            )
        ),
        2027 to HolidayYearData(status = HolidayYearStatus.NOT_PUBLISHED)
    )

    @Volatile
    private var remoteYears: Map<Int, HolidayYearData> = emptyMap()

    fun load(context: Context) {
        val payload = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(PAYLOAD_KEY, null)
            ?: return
        parsePayload(payload)?.let { remoteYears = it }
    }

    fun applyRemotePayload(context: Context, payload: String): Boolean {
        val parsed = parsePayload(payload) ?: return false
        remoteYears = parsed
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PAYLOAD_KEY, payload)
            .apply()
        return true
    }

    fun statusForYear(year: Int): HolidayYearStatus =
        (remoteYears[year] ?: builtInYears[year])?.status ?: HolidayYearStatus.LOCAL_FALLBACK

    fun isWorkday(calendar: Calendar): Boolean = isWorkday(
        LocalDate.of(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    )

    fun isWorkday(date: LocalDate): Boolean {
        val data = remoteYears[date.year] ?: builtInYears[date.year]
        if (data?.status == HolidayYearStatus.PUBLISHED) {
            if (date in data.adjustedWorkdays) return true
            if (date in data.holidays) return false
        }
        return date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }

    private fun parsePayload(payload: String): Map<Int, HolidayYearData>? = runCatching {
        val years = JSONObject(payload).optJSONObject("years") ?: return@runCatching null
        val parsed = buildMap {
            val keys = years.keys()
            while (keys.hasNext()) {
                val yearText = keys.next()
                val year = yearText.toInt()
                val yearJson = years.getJSONObject(yearText)
                val status = when (yearJson.optString("status", "PUBLISHED").uppercase(Locale.ROOT)) {
                    "PUBLISHED" -> HolidayYearStatus.PUBLISHED
                    "NOT_PUBLISHED" -> HolidayYearStatus.NOT_PUBLISHED
                    else -> error("未知的节假日数据状态")
                }
                val holidays = readDates(yearJson, "holidays", year)
                val adjustedWorkdays = readDates(yearJson, "adjustedWorkdays", year)
                if (holidays.intersect(adjustedWorkdays).isNotEmpty()) {
                    error("同一天不能同时是放假日和调休工作日")
                }
                put(year, HolidayYearData(status, holidays, adjustedWorkdays))
            }
        }
        parsed.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun readDates(yearJson: JSONObject, key: String, year: Int): Set<LocalDate> {
        val dates = yearJson.optJSONArray(key) ?: return emptySet()
        return buildSet {
            for (index in 0 until dates.length()) {
                val date = LocalDate.parse(dates.getString(index))
                require(date.year == year) { "$key 中包含错误年份" }
                add(date)
            }
        }
    }
}
