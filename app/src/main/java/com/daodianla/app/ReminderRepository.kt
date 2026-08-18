package com.daodianla.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class ReminderRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<Reminder> {
        val raw = preferences.getString(KEY_REMINDERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    runCatching { array.getJSONObject(index).toReminder() }
                        .getOrNull()
                        ?.let(::add)
                }
            }.sortedWith(compareBy<Reminder> { !it.enabled }.thenBy { it.timeMinutes })
        }.getOrDefault(emptyList())
    }

    fun getById(id: Long): Reminder? = getAll().firstOrNull { it.id == id }

    /** 首次启动生成一条方便联调的单次提醒，用户删除后不会再次自动生成。 */
    fun createTestReminderIfNeeded(): Reminder? {
        if (preferences.getBoolean(KEY_SAMPLE_CREATED, false)) return null

        preferences.edit().putBoolean(KEY_SAMPLE_CREATED, true).commit()
        if (getAll().isNotEmpty()) return null

        val trigger = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return Reminder(
            id = System.currentTimeMillis(),
            title = "测试提醒（可修改）",
            timeMinutes = trigger.get(Calendar.HOUR_OF_DAY) * 60 + trigger.get(Calendar.MINUTE),
            durationHours = 1,
            repeatMode = RepeatMode.ONCE,
            triggerAtMillis = trigger.timeInMillis
        ).also { save(it) }
    }

    fun save(reminder: Reminder) {
        val updated = getAll().filterNot { it.id == reminder.id } + reminder
        write(updated)
    }

    fun delete(id: Long) {
        write(getAll().filterNot { it.id == id })
    }

    private fun write(reminders: List<Reminder>) {
        val array = JSONArray()
        reminders.sortedBy { it.id }.forEach { reminder ->
            array.put(
                JSONObject().apply {
                    put("id", reminder.id)
                    put("title", reminder.title)
                    put("timeMinutes", reminder.timeMinutes)
                    put("durationHours", reminder.durationHours)
                    put("repeatMode", reminder.repeatMode.name)
                    put("enabled", reminder.enabled)
                    reminder.triggerAtMillis?.let { put("triggerAtMillis", it) }
                }
            )
        }
        // 用户可能保存后立即清理后台；同步提交可确保闹钟唤醒新进程时一定能读到提醒。
        preferences.edit().putString(KEY_REMINDERS, array.toString()).commit()
    }

    private fun JSONObject.toReminder(): Reminder = Reminder(
        id = getLong("id"),
        title = getString("title"),
        timeMinutes = getInt("timeMinutes"),
        durationHours = getInt("durationHours"),
        repeatMode = runCatching {
            RepeatMode.valueOf(getString("repeatMode"))
        }.getOrDefault(RepeatMode.ONCE),
        enabled = optBoolean("enabled", true),
        triggerAtMillis = optLong("triggerAtMillis", 0L).takeIf { it > 0L }
    )

    private companion object {
        const val PREFERENCES_NAME = "dao_dian_la_preferences"
        const val KEY_REMINDERS = "reminders"
        const val KEY_SAMPLE_CREATED = "sample_reminder_created"
    }
}
