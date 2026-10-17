package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import java.util.Calendar

class StadiumNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val manager = StadiumNotificationManager(context)
        val type = intent?.getStringExtra(StadiumNotificationManager.EXTRA_NOTIFICATION_TYPE) ?: "daily_challenge"
        if (type == "daily_challenge") {
            val title = intent?.getStringExtra("challenge_title") ?: "تحدي اليوم بانتظارك! 🎯"
            val game = intent?.getStringExtra("game_name") ?: "ألعاب الصالون"
            val target = intent?.getStringExtra("target_desc") ?: "حقق الهدف اليومي واحصد كؤوس الذهب ⭐"
            manager.showDailyChallengeReminderNow(title, game, target)
        } else {
            manager.showSabahoReminderNow()
        }
    }
}

class StadiumNotificationManager(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "stadium_daily_challenge"
        const val NOTIFICATION_ID_SABAHO = 1001
        const val NOTIFICATION_ID_DAILY = 1002
        const val EXTRA_NAVIGATE_TO = "extra_navigate_to"
        const val EXTRA_NOTIFICATION_TYPE = "notification_type"
        const val DESTINATION_SABAHO = "sabaho"
        const val DESTINATION_DAILY_CHALLENGE = "daily_challenge"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "تذكيرات ملعب التحدي اليومية"
            val descriptionText = "تنبيهات تحدي اليوم وألعاب الصالون للحفاظ على سلسلة الإنجازات"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showDailyChallengeReminderNow(
        challengeTitle: String = "تحدي اليوم الجديد متاح الآن! 🎯",
        gameName: String = "أتوبيس كومبليت",
        targetDesc: String = "حطم الهدف المطلوب اليوم واكسب +200 نقطة ⭐ و +75 عملة 🪙"
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, DESTINATION_DAILY_CHALLENGE)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🎯 $challengeTitle ($gameName)")
            .setContentText(targetDesc)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("🎯 $challengeTitle\nاللعبة: $gameName\nالهدف: $targetDesc\n\nاضغط هنا لفتح التحدي فوراً والمنافسة بالصوت والذكاء الاصطناعي! 🚀🏆")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DAILY, notification)
    }

    fun showSabahoReminderNow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAVIGATE_TO, DESTINATION_SABAHO)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔥 لا تكسر سلسلة انتصاراتك اليومية!")
            .setContentText("جولة جديدة من 'صباحو تحدي' بانتظارك! أثبت معلوماتك التكتيكية واحصد كؤوس الذهب 🏆⚽")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("جولة جديدة ومثيرة من 'صباحو تحدي' تنتظرك الآن في ملعب التحدي! استخدم وسائل المساعدة الثلاث (50:50، تغيير السؤال، والوقت الإضافي) للمحافظة على سلسلة انتصاراتك النارية 🔥⚽🏆")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SABAHO, notification)
    }

    fun scheduleDailyChallengeReminder(
        challengeTitle: String,
        gameName: String,
        targetDesc: String,
        hour: Int = 12,
        minute: Int = 0
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, StadiumNotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TYPE, "daily_challenge")
            putExtra("challenge_title", challengeTitle)
            putExtra("game_name", gameName)
            putExtra("target_desc", targetDesc)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2003,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: Exception) {}
    }

    fun scheduleDailySabahoReminder() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, StadiumNotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TYPE, "sabaho")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 11) // 11:00 AM daily
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        try {
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } catch (_: Exception) {}
    }
}
