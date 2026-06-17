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
        
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQ_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel any existing alarm
        alarmManager.cancel(pendingIntent)

        // Find the next notification time
        val nextTriggerTime = getNextTriggerTimeMillis()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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

    private fun getNextTriggerTimeMillis(): Long {
        val now = Calendar.getInstance()
        val targetHours = listOf(8, 12, 16, 20)

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
        if (nextHour != -1) {
            targetCal.set(Calendar.HOUR_OF_DAY, nextHour)
            targetCal.set(Calendar.MINUTE, 0)
            targetCal.set(Calendar.SECOND, 0)
            targetCal.set(Calendar.MILLISECOND, 0)
        } else {
            // Tomorrow at 8 AM
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
            targetCal.set(Calendar.HOUR_OF_DAY, 8)
            targetCal.set(Calendar.MINUTE, 0)
            targetCal.set(Calendar.SECOND, 0)
            targetCal.set(Calendar.MILLISECOND, 0)
        }

        return targetCal.timeInMillis
    }
}
