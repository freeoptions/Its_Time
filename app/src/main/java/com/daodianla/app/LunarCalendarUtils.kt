package com.daodianla.app

import android.icu.util.Calendar as IcuCalendar
import android.icu.util.ChineseCalendar
import java.util.Calendar

data class LunarDate(
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean
)

object LunarCalendarUtils {
    fun getLunarDate(timeInMillis: Long = System.currentTimeMillis()): LunarDate {
        val calendar = ChineseCalendar().apply { this.timeInMillis = timeInMillis }
        return LunarDate(
            month = calendar.get(IcuCalendar.MONTH) + 1,
            day = calendar.get(IcuCalendar.DAY_OF_MONTH),
            isLeapMonth = calendar.get(IcuCalendar.IS_LEAP_MONTH) != 0
        )
    }
}

private val SOLAR_FESTIVALS = mapOf(
    (1 to 1) to "元旦",
    (2 to 14) to "情人节",
    (3 to 8) to "妇女节",
    (4 to 1) to "愚人节",
    (5 to 1) to "劳动节",
    (5 to 4) to "青年节",
    (6 to 1) to "儿童节",
    (7 to 1) to "建党节",
    (8 to 1) to "建军节",
    (9 to 10) to "教师节",
    (10 to 1) to "国庆节",
    (12 to 24) to "平安夜",
    (12 to 25) to "圣诞节"
)

private val LUNAR_FESTIVALS = mapOf(
    (1 to 1) to "春节",
    (1 to 15) to "元宵节",
    (5 to 5) to "端午节",
    (7 to 7) to "七夕",
    (8 to 15) to "中秋节",
    (9 to 9) to "重阳节",
    (12 to 8) to "腊八节",
    (12 to 23) to "北方小年",
    (12 to 24) to "南方小年"
)

private val LUNAR_MONTH_NAMES = listOf(
    "", "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"
)

private val LUNAR_DAY_NAMES = listOf(
    "", "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
    "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
    "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
)

fun formatLunarDate(date: LunarDate): String {
    val monthName = LUNAR_MONTH_NAMES.getOrNull(date.month).orEmpty()
    val dayName = LUNAR_DAY_NAMES.getOrNull(date.day).orEmpty()
    val leapPrefix = if (date.isLeapMonth) "闰" else ""
    return "${leapPrefix}${monthName}月$dayName"
}

fun findTodayFestival(calendar: Calendar, lunarDate: LunarDate): String? {
    val solarKey = (calendar.get(Calendar.MONTH) + 1) to calendar.get(Calendar.DAY_OF_MONTH)
    return SOLAR_FESTIVALS[solarKey] ?: LUNAR_FESTIVALS[lunarDate.month to lunarDate.day]
}
