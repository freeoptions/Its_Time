package com.daodianla.app

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReminderExportManager {
    private const val PREFERENCES_NAME = "dao_dian_la_preferences"
    private const val KEY_EXPORT_DIRECTORY_URI = "export_directory_uri"
    private const val KEY_LAST_AUTO_BACKUP_AT = "last_auto_backup_at"
    private const val AUTO_BACKUP_INTERVAL_MILLIS = 3L * 24L * 60L * 60L * 1000L
    private const val FORMAT_NAME = "dao_dian_la_reminders"
    private const val FORMAT_VERSION = 1

    fun getExportDirectory(context: Context): String? = context
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getString(KEY_EXPORT_DIRECTORY_URI, null)
        ?.takeIf(String::isNotBlank)

    fun setExportDirectory(context: Context, rawDirectory: String) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EXPORT_DIRECTORY_URI, rawDirectory)
            .remove(KEY_LAST_AUTO_BACKUP_AT)
            .apply()
    }

    fun clearExportDirectory(context: Context) {
        val rawDirectory = getExportDirectory(context)
        runCatching {
            rawDirectory?.let { Uri.parse(it) }?.let { uri ->
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_EXPORT_DIRECTORY_URI)
            .remove(KEY_LAST_AUTO_BACKUP_AT)
            .apply()
    }

    fun createFileName(timestamp: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH_mm_ss", Locale.CHINA)
        return "到点啦_exportConfig_${formatter.format(Date(timestamp))}.json"
    }

    fun exportToDirectory(
        context: Context,
        rawDirectory: String,
        reminders: List<Reminder>
    ): String {
        val savedName = writeTextToDirectory(
            context = context,
            rawDirectory = rawDirectory,
            fileName = createFileName(),
            contents = buildJson(reminders)
        )
        markExported(context)
        return savedName
    }

    fun exportToUri(context: Context, uri: Uri, reminders: List<Reminder>) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(buildJson(reminders).toByteArray(Charsets.UTF_8))
        } ?: throw IOException("无法写入导出文件")
    }

    /**
     * 应用进入前台时按 3 天间隔静默备份到用户已选择的目录。
     * 失败时不改变时间戳，下次进入前台仍会重试。
     */
    fun maybeAutoBackup(context: Context, reminders: List<Reminder>): Boolean {
        val rawDirectory = getExportDirectory(context) ?: return false
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastBackupAt = preferences.getLong(KEY_LAST_AUTO_BACKUP_AT, 0L)
        if (lastBackupAt > 0L && now - lastBackupAt < AUTO_BACKUP_INTERVAL_MILLIS) return false

        return runCatching {
            writeTextToDirectory(
                context = context,
                rawDirectory = rawDirectory,
                fileName = createFileName(now),
                contents = buildJson(reminders, now)
            )
            markExported(context, now)
            true
        }.getOrDefault(false)
    }

    private fun markExported(context: Context, timestamp: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTO_BACKUP_AT, timestamp)
            .apply()
    }

    private fun buildJson(
        reminders: List<Reminder>,
        exportedAt: Long = System.currentTimeMillis()
    ): String = JSONObject().apply {
        put("format", FORMAT_NAME)
        put("version", FORMAT_VERSION)
        put("exportedAt", exportedAt)
        put("taskCount", reminders.size)
        put("reminders", JSONArray().apply {
            reminders.sortedBy { it.id }.forEach { put(it.toJson()) }
        })
    }.toString(4)

    private fun Reminder.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("timeMinutes", timeMinutes)
        put("durationHours", durationHours)
        put("repeatMode", repeatMode.name)
        put("enabled", enabled)
        triggerAtMillis?.let { put("triggerAtMillis", it) }
    }
}
