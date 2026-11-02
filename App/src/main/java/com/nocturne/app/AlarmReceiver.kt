package com.nocturne.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val kind = intent.getStringExtra("kind") ?: "alarm"
        val prefs = context.getSharedPreferences("nocturne_data", Context.MODE_PRIVATE)
        val items = runCatching { JSONArray(prefs.getString(kind, "[]")) }.getOrElse { JSONArray() }
        var item: org.json.JSONObject? = null
        for (i in 0 until items.length()) {
            val candidate = items.optJSONObject(i) ?: continue
            if (candidate.optString("id") == id) { item = candidate; break }
        }
        val alarm = item ?: return
        if (!alarm.optBoolean("enabled", true)) return

        // For repeating schedules, calculate and register the next occurrence immediately.
        if (alarm.optString("repeat", "Один раз") != "Один раз") Scheduler.schedule(context, alarm)
        // A non-confirming one-time alarm is marked complete now. Snooze explicitly
        // re-enables it before scheduling, so the snoozed occurrence can still fire.
        if (alarm.optString("repeat", "Один раз") == "Один раз" && !alarm.optBoolean("confirm", false)) {
            alarm.put("enabled", false)
            for (i in 0 until items.length()) if (items.optJSONObject(i)?.optString("id") == id) items.put(i, alarm)
            prefs.edit().putString(kind, items.toString()).apply()
        }
        if (alarm.optString("repeat", "Один раз") == "Один раз" && alarm.optBoolean("confirm", false)) {
            val again = Intent(context, AlarmReceiver::class.java).putExtra("id", id).putExtra("kind", kind)
            val pi = PendingIntent.getBroadcast(context, id.hashCode(), again, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val at = System.currentTimeMillis() + 5 * 60_000L
            try { am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, pi) }
            catch (_: SecurityException) { am.set(android.app.AlarmManager.RTC_WAKEUP, at, pi) }
        }

        prefs.edit().putString("active_id", id).putString("active_kind", kind).apply()
        val service = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_RING
            putExtra("id", id)
            putExtra("kind", kind)
            putExtra("title", alarm.optString("title", "Будильник"))
            putExtra("tone", alarm.optString("tone", "builtin:soft"))
            putExtra("vibrate", alarm.optBoolean("vibrate", true))
            putExtra("volume_mode", alarm.optString("volumeMode", "ramp"))
        }
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED && intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val prefs = context.getSharedPreferences("nocturne_data", Context.MODE_PRIVATE)
        listOf("alarm", "reminder").forEach { kind ->
            val items = runCatching { JSONArray(prefs.getString(kind, "[]")) }.getOrElse { JSONArray() }
            for (i in 0 until items.length()) items.optJSONObject(i)?.let { Scheduler.schedule(context, it) }
        }
    }
}
