package com.fanta.androidsport

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object NotificationScheduler {
    private const val ALARM_REQ_CODE = 4444

    fun scheduleNextAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        
        // Find the next notification time and its target hour
        val (nextTriggerTime, targetHour) = getNextTriggerTimeAndHour()
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("scheduled_hour", targetHour)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel any existing alarm
        alarmManager.cancel(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerTime,
                    pendingIntent
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextTriggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                nextTriggerTime,
                pendingIntent
            )
        }
    }

    private fun getNextTriggerTimeAndHour(): Pair<Long, Int> {
        val now = Calendar.getInstance()
        val targetHours = listOf(10, 18)

        var nextHour = -1
        for (hour in targetHours) {
            val targetCal = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (targetCal.after(now)) {
                nextHour = hour
                break
            }
        }

        val targetCal = Calendar.getInstance()
        val finalHour = if (nextHour != -1) nextHour else 10
        if (nextHour != -1) {
            targetCal.set(Calendar.HOUR_OF_DAY, nextHour)
            targetCal.set(Calendar.MINUTE, 0)
            targetCal.set(Calendar.SECOND, 0)
            targetCal.set(Calendar.MILLISECOND, 0)
        } else {
            // Tomorrow at 10 AM
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
            targetCal.set(Calendar.HOUR_OF_DAY, 10)
            targetCal.set(Calendar.MINUTE, 0)
            targetCal.set(Calendar.SECOND, 0)
            targetCal.set(Calendar.MILLISECOND, 0)
        }

        return Pair(targetCal.timeInMillis, finalHour)
    }
}
