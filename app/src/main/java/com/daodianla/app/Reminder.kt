package com.daodianla.app

import java.time.LocalDate

enum class RepeatMode {
    ONCE,
    DAILY,
    WEEKDAYS,
    SELECTED_DATES
}

data class Reminder(
    val id: Long,
    val title: String,
    val timeMinutes: Int,
    val durationHours: Int,
    val repeatMode: RepeatMode,
    val enabled: Boolean = true,
    /** 单次提醒的绝对触发时间；重复提醒继续按本地时分计算。 */
    val triggerAtMillis: Long? = null,
    /** 指定日期提醒使用的日期集合，按本地日期保存，不受时区切换影响。 */
    val selectedDates: List<LocalDate> = emptyList()
) {
    val hour: Int get() = timeMinutes / 60
    val minute: Int get() = timeMinutes % 60
    val notificationId: Int get() = (id xor (id ushr 32)).toInt() and Int.MAX_VALUE
}
