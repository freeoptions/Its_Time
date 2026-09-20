package com.daodianla.app

import android.app.Application

class DaoDianLaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ChineseWorkdayCalendar.load(this)
        ReminderEventLog.append(
            this,
            ReminderLogType.PROCESS_STARTED,
            "应用进程由系统创建"
        )
    }
}
