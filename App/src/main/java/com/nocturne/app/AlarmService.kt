package com.nocturne.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rampStep = 0
    private var ringing = false
    private var activeId = ""
    private var activeKind = "alarm"
    private val prefs by lazy { getSharedPreferences("nocturne_data", MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                activeId = intent.getStringExtra("id") ?: ""
                activeKind = intent.getStringExtra("kind") ?: "alarm"
                val title = intent.getStringExtra("title") ?: "Напоминание"
                val tone = intent.getStringExtra("tone") ?: "builtin:soft"
                val vibrate = intent.getBooleanExtra("vibrate", true)
                val ramp = intent.getStringExtra("volume_mode") != "full"
                prefs.edit().putString("active_id", activeId).putString("active_kind", activeKind).apply()
                ringing = true
                startForeground(NOTIFICATION_ID, buildNotification(true, title))
                startSound(tone, vibrate, ramp)
            }
            ACTION_STOP -> stopEverything()
            ACTION_CANCEL_CURRENT -> cancelCurrent()
            ACTION_SNOOZE -> snoozeCurrent(intent.getIntExtra("minutes", 5))
            ACTION_UPDATE, null -> {
                if (!ringing) startForeground(NOTIFICATION_ID, buildNotification(false, upcomingText()))
                else updateNotification()
            }
        }
        return START_STICKY
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_STATUS) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL_STATUS, "Nocturne · расписание", NotificationManager.IMPORTANCE_LOW).apply { description = "Ближайшие будильники и состояние приложения"; setSound(null, null); enableVibration(false) })
            }
            if (manager.getNotificationChannel(CHANNEL_RING) == null) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL_RING, "Nocturne · будильник", NotificationManager.IMPORTANCE_HIGH).apply { description = "Звуковые сигналы будильника"; setSound(null, null); enableVibration(true) })
            }
        }
    }

    private fun actionIntent(action: String, request: Int): PendingIntent {
        val i = Intent(this, AlarmService::class.java).setAction(action)
        return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(this, request, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        else PendingIntent.getService(this, request, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildNotification(isRinging: Boolean, detail: String): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (isRinging) "⏰ СЕЙЧАС: $detail" else "Nocturne работает в фоне"
        val text = if (isRinging) "Напоминание активно · выберите действие ниже" else detail
        val channel = if (isRinging) CHANNEL_RING else CHANNEL_STATUS
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title).setContentText(text).setStyle(Notification.BigTextStyle().bigText(text + if (isRinging) "\nСиний - отложить на 5 минут. Оранжевый - отменить текущее срабатывание. Красный - остановить Nocturne." else "\nNocturne отслеживает расписание будильников."))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(if (isRinging) Notification.CATEGORY_ALARM else Notification.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_media_pause, "🔴 Стоп", actionIntent(ACTION_STOP, 101))
            .addAction(android.R.drawable.ic_delete, "🟠 Отмена", actionIntent(ACTION_CANCEL_CURRENT, 102))
            .addAction(android.R.drawable.ic_media_play, "🔵 +5 мин", actionIntent(ACTION_SNOOZE, 103))
        return b.build()
    }

    private fun upcomingText(): String {
        val upcoming = mutableListOf<Pair<Long, String>>()
        listOf("alarm", "reminder").forEach { kind ->
            val arr = runCatching { JSONArray(prefs.getString(kind, "[]")) }.getOrElse { JSONArray() }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (!o.optBoolean("enabled", true)) continue
                val next = Scheduler.nextTime(o) ?: continue
                upcoming.add(next.timeInMillis to "${o.optString("time")} · ${o.optString("title")}")
            }
        }
        return if (upcoming.isEmpty()) "Нет активных будильников" else upcoming.sortedBy { it.first }.take(3).joinToString("  •  ") { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(it.first)) + " ${it.second.substringAfter(" · ")}" }
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(ringing, if (ringing) "Напоминание активно" else upcomingText()))
    }

    private fun startSound(tone: String, vibrate: Boolean, ramp: Boolean) {
        stopPlayer()
        val uri = when (tone) {
            "builtin:soft" -> Uri.parse("android.resource://$packageName/${R.raw.soft}")
            "builtin:chime" -> Uri.parse("android.resource://$packageName/${R.raw.chime}")
            "default" -> android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            else -> runCatching { Uri.parse(tone) }.getOrNull()
        } ?: Uri.parse("android.resource://$packageName/${R.raw.soft}")
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setOnPreparedListener { mp ->
                    mp.start()
                    if (ramp) rampVolume() else mp.setVolume(1f, 1f)
                }
                setOnErrorListener { _, _, _ ->
                    if (uri.toString() != "android.resource://$packageName/${R.raw.chime}") startSound("builtin:chime", vibrate, ramp)
                    true
                }
                prepareAsync()
            }
            if (vibrate) vibrateAlarm()
        } catch (_: Exception) {
            runCatching { startSound("builtin:chime", vibrate, ramp) }
        }
    }


    @Suppress("DEPRECATION")
    private fun cancelVibration() {
        if (Build.VERSION.SDK_INT >= 31) runCatching { (getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator.cancel() }
        else runCatching { (getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator).cancel() }
    }

    @Suppress("DEPRECATION")
    private fun vibrateAlarm() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) (getSystemService(VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator else getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 700, 400), 0)) else vibrator.vibrate(longArrayOf(0, 700, 400), 0)
    }

    private fun rampVolume() {
        rampStep = 0
        val task = object : Runnable {
            override fun run() {
                val p = player ?: return
                rampStep++
                val v = (rampStep / 12f).coerceIn(0.08f, 1f)
                p.setVolume(v, v)
                if (rampStep < 12 && ringing) handler.postDelayed(this, 250)
            }
        }
        handler.post(task)
    }

    private fun stopPlayer() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }; runCatching { player?.release() }; player = null
        cancelVibration()
    }

    private fun stopEverything() {
        disableOneTimeCurrent()
        ringing = false
        stopPlayer()
        prefs.edit().remove("active_id").remove("active_kind").apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun disableOneTimeCurrent() {
        val id = activeId.ifBlank { prefs.getString("active_id", "") ?: "" }
        val kind = activeKind.ifBlank { prefs.getString("active_kind", "alarm") ?: "alarm" }
        if (id.isBlank()) return
        val arr = runCatching { JSONArray(prefs.getString(kind, "[]")) }.getOrElse { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optString("id") == id && o.optString("repeat") == "Один раз") {
                o.put("enabled", false); arr.put(i, o); prefs.edit().putString(kind, arr.toString()).apply(); Scheduler.cancel(this, id)
            }
        }
    }

    private fun cancelCurrent() {
        disableOneTimeCurrent()
        ringing = false; stopPlayer(); prefs.edit().remove("active_id").remove("active_kind").apply(); updateNotification()
    }

    private fun snoozeCurrent(minutes: Int) {
        val id = activeId.ifBlank { prefs.getString("active_id", "") ?: "" }
        val kind = activeKind.ifBlank { prefs.getString("active_kind", "alarm") ?: "alarm" }
        if (id.isNotBlank()) {
            val arr = runCatching { JSONArray(prefs.getString(kind, "[]")) }.getOrElse { JSONArray() }
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optString("id") == id && o.optString("repeat") == "Один раз") { o.put("enabled", true); arr.put(i, o); prefs.edit().putString(kind, arr.toString()).apply() }
            }
            val pi = PendingIntent.getBroadcast(this, id.hashCode(), Intent(this, AlarmReceiver::class.java).putExtra("id", id).putExtra("kind", kind), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val at = System.currentTimeMillis() + minutes.coerceIn(1, 120) * 60_000L
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (_: SecurityException) { am.set(AlarmManager.RTC_WAKEUP, at, pi) }
        }
        ringing = false; stopPlayer(); prefs.edit().remove("active_id").remove("active_kind").apply(); updateNotification()
    }

    override fun onDestroy() { stopPlayer(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_RING = "com.nocturne.app.RING"
        const val ACTION_UPDATE = "com.nocturne.app.UPDATE"
        const val ACTION_STOP = "com.nocturne.app.STOP"
        const val ACTION_CANCEL_CURRENT = "com.nocturne.app.CANCEL_CURRENT"
        const val ACTION_SNOOZE = "com.nocturne.app.SNOOZE"
        const val CHANNEL_STATUS = "nocturne_status_v2"
        const val CHANNEL_RING = "nocturne_ring_v2"
        const val NOTIFICATION_ID = 7603
    }
}
