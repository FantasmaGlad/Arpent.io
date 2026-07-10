package com.fanta.androidsport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            NotificationScheduler.scheduleNextAlarm(context)
            return
        }

        val prefs = context.getSharedPreferences("arpent_prefs", Context.MODE_PRIVATE)
        val pseudonyme = prefs.getString("user_pseudonyme", "conquérant") ?: "conquérant"
        val userId = prefs.getString("user_id", null)

        val scheduledHour = intent.getIntExtra("scheduled_hour", -1)
        val targetHour = if (scheduledHour != -1) {
            scheduledHour
        } else {
            // Fallback checking current hour
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (hour in 9..11) 10 else 18
        }

        val title = if (targetHour == 10) "Course matinale" else "Récolte des points"
        val message = if (targetHour == 10) {
            "$pseudonyme Il fait beau dehors, viens on va courir !"
        } else {
            "$pseudonyme tu viens de gagner 50 xp !"
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "arpent_daily_reminders"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Rappels quotidiens",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Arpent.io")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        
        // Sync notification & update XP in the database if user is authenticated
        if (userId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Update XP in database at 18:00
                    if (targetHour == 18) {
                        val currentXp = prefs.getInt("user_xp", 0)
                        val newXp = currentXp + 50
                        supabase.postgrest["profiles"].update(
                            mapOf("xp" to newXp)
                        ) {
                            filter { eq("id", userId) }
                        }
                        prefs.edit().putInt("user_xp", newXp).apply()
                    }

                    // Insert notification record
                    supabase.postgrest["notifications"].insert(
                        mapOf(
                            "utilisateur_id" to userId,
                            "type" to if (targetHour == 10) 5 else 6,
                            "titre" to title,
                            "message" to message,
                            "lu" to false,
                            "metadata" to mapOf("xp_gained" to if (targetHour == 10) 0 else 50)
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("NotificationReceiver", "Failed to sync daily notification to Supabase", e)
                }
            }
        }

        // Re-schedule the alarm for the next time this trigger is scheduled
        NotificationScheduler.scheduleNextAlarm(context)
    }
}
