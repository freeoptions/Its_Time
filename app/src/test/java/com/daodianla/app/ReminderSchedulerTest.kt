package com.daodianla.app

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReminderSchedulerTest {
    @Test
    fun dailyReminder_movesToTomorrowAfterTodayTime() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val reminder = Reminder(1L, "测试", 18 * 60, 6, RepeatMode.DAILY)

        val next = Calendar.getInstance().apply {
            timeInMillis = ReminderScheduler.nextTriggerMillis(reminder, now.timeInMillis)
        }

        assertTrue(next.timeInMillis > now.timeInMillis)
        assertTrue(next.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun weekdayReminder_skipsSaturdayAndSunday() {
        val friday = Calendar.getInstance().apply {
            while (get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val reminder = Reminder(2L, "测试", 18 * 60, 6, RepeatMode.WEEKDAYS)

        val next = Calendar.getInstance().apply {
            timeInMillis = ReminderScheduler.nextTriggerMillis(reminder, friday.timeInMillis)
        }

        assertTrue(next.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
    }

    @Test
    fun weekdayReminder_includesOfficialAdjustedSunday() {
        val sundayBeforeReminder = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 19)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val reminder = Reminder(7L, "测试", 17 * 60 + 30, 6, RepeatMode.WEEKDAYS)

        val next = Calendar.getInstance().apply {
            timeInMillis = ReminderScheduler.nextTriggerMillis(
                reminder,
                sundayBeforeReminder.timeInMillis
            )
        }

        assertEquals(2026, next.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, next.get(Calendar.MONTH))
        assertEquals(20, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SUNDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun weekdayReminder_skipsOfficialHolidayOnWeekday() {
        val holidayBeforeReminder = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 24)
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val reminder = Reminder(8L, "测试", 9 * 60, 6, RepeatMode.WEEKDAYS)

        val next = Calendar.getInstance().apply {
            timeInMillis = ReminderScheduler.nextTriggerMillis(
                reminder,
                holidayBeforeReminder.timeInMillis
            )
        }

        assertEquals(2026, next.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, next.get(Calendar.MONTH))
        assertEquals(28, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun onceReminder_keepsItsPersistedAbsoluteTrigger() {
        val now = 1_000_000L
        val expected = now + 60_000L
        val reminder = Reminder(
            id = 3L,
            title = "测试",
            timeMinutes = 12 * 60,
            durationHours = 1,
            repeatMode = RepeatMode.ONCE,
            triggerAtMillis = expected
        )

        assertEquals(expected, ReminderScheduler.nextTriggerMillis(reminder, now))
    }

    @Test
    fun missedOnceReminder_isScheduledForImmediateCatchUp() {
        val now = 2_000_000L
        val reminder = Reminder(
            id = 4L,
            title = "测试",
            timeMinutes = 12 * 60,
            durationHours = 1,
            repeatMode = RepeatMode.ONCE,
            triggerAtMillis = now - 60_000L
        )

        val catchUpAt = ReminderScheduler.scheduleTriggerMillis(reminder, now)

        assertTrue(catchUpAt > now)
        assertTrue(catchUpAt <= now + 2_000L)
    }

    @Test
    fun missedOnceReminder_reconciledOnAppOpen_isMarkedAsAppCatchUp() {
        val now = 3_000_000L
        val reminder = Reminder(
            id = 5L,
            title = "测试",
            timeMinutes = 12 * 60,
            durationHours = 1,
            repeatMode = RepeatMode.ONCE,
            triggerAtMillis = now - 60_000L
        )

        val source = ReminderScheduler.deliverySource(
            reminder,
            ReminderScheduler.nextTriggerMillis(reminder, now),
            now,
            ReminderScheduleSource.APP_FOREGROUND_RECONCILE
        )

        assertEquals(ReminderDeliverySource.APP_OPEN_CATCH_UP, source)
    }

    @Test
    fun futureReminder_reconciledOnAppOpen_remainsSystemAlarmDelivery() {
        val now = 4_000_000L
        val reminder = Reminder(
            id = 6L,
            title = "测试",
            timeMinutes = 12 * 60,
            durationHours = 1,
            repeatMode = RepeatMode.ONCE,
            triggerAtMillis = now + 60_000L
        )

        val source = ReminderScheduler.deliverySource(
            reminder,
            ReminderScheduler.nextTriggerMillis(reminder, now),
            now,
            ReminderScheduleSource.APP_FOREGROUND_RECONCILE
        )

        assertEquals(ReminderDeliverySource.SYSTEM_ALARM, source)
    }
}
