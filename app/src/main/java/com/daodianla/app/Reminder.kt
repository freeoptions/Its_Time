package com.daodianla.app

enum class RepeatMode {
    ONCE,
    DAILY,
    WEEKDAYS
}

data class Reminder(
    val id: Long,
    val title: String,
    val timeMinutes: Int,
    val durationHours: Int,
    val repeatMode: RepeatMode,
    val enabled: Boolean = true,
    /** 单次提醒的绝对触发时间；重复提醒继续按本地时分计算。 */
    val triggerAtMillis: Long? = null
) {
    val hour: Int get() = timeMinutes / 60
    val minute: Int get() = timeMinutes % 60
    val notificationId: Int get() = (id xor (id ushr 32)).toInt() and Int.MAX_VALUE
}
