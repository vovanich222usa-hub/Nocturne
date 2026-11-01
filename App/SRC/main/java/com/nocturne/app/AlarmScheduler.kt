package com.nocturne.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Scheduler {
    fun schedule(context: Context, item: JSONObject) {
        if (!item.optBoolean("enabled", true)) return
        val id=item.optString("id"); if(id.isBlank()) return
        val cal=nextTime(item) ?: return
        val intent=Intent(context,AlarmReceiver::class.java).apply { putExtra("id",id); putExtra("title",item.optString("title")); putExtra("kind",item.optString("kind")); putExtra("tone",item.optString("tone","default")); putExtra("confirm",item.optBoolean("confirm")); putExtra("vibrate",item.optBoolean("vibrate",true)) }
        val pi=PendingIntent.getBroadcast(context,id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try { if(Build.VERSION.SDK_INT>=23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi) else am.setExact(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi) }
        catch(_:SecurityException) { am.set(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi) }
    }
    fun cancel(context:Context,id:String){val pi=PendingIntent.getBroadcast(context,id.hashCode(),Intent(context,AlarmReceiver::class.java),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE);if(pi!=null)(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)}
    fun nextTime(o:JSONObject):Calendar? {
        val parts=o.optString("time","09:00").split(":"); if(parts.size!=2)return null
        val h=parts[0].toIntOrNull()?:return null;val m=parts[1].toIntOrNull()?:return null
        val now=Calendar.getInstance(); val c=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,h);set(Calendar.MINUTE,m);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}
        val rule=o.optString("repeat","Один раз"); val date=o.optString("date")
        if(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))){try{val d=SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(date)!!;val dc=Calendar.getInstance().apply{time=d};if(rule=="Один раз")c.set(dc.get(Calendar.YEAR),dc.get(Calendar.MONTH),dc.get(Calendar.DAY_OF_MONTH)) else if(dc.timeInMillis>now.timeInMillis && c.timeInMillis<now.timeInMillis)c.set(dc.get(Calendar.YEAR),dc.get(Calendar.MONTH),dc.get(Calendar.DAY_OF_MONTH))}catch(_:Exception){}}
        val duePassed = c.timeInMillis <= now.timeInMillis
        if (duePassed) {
            when (rule) {
                "Каждую неделю" -> c.add(Calendar.DAY_OF_YEAR, 7)
                "Каждый месяц" -> c.add(Calendar.MONTH, 1)
                "Каждый год" -> c.add(Calendar.YEAR, 1)
                "Каждые N дней" -> c.add(Calendar.DAY_OF_YEAR, o.optInt("interval",1).coerceAtLeast(1))
                "Каждые N часов" -> { val step=o.optInt("interval",1).coerceAtLeast(1); var guard=0; while(c.timeInMillis<=now.timeInMillis && guard++<48)c.add(Calendar.HOUR_OF_DAY,step) }
                else -> c.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        when(rule){
            "Один раз" -> Unit
            "По будням" -> while(c.get(Calendar.DAY_OF_WEEK)==Calendar.SATURDAY||c.get(Calendar.DAY_OF_WEEK)==Calendar.SUNDAY)c.add(Calendar.DAY_OF_YEAR,1)
            "По выходным" -> while(c.get(Calendar.DAY_OF_WEEK)!=Calendar.SATURDAY&&c.get(Calendar.DAY_OF_WEEK)!=Calendar.SUNDAY)c.add(Calendar.DAY_OF_YEAR,1)
            "Выбранные дни недели" -> {val map=mapOf("пн" to Calendar.MONDAY,"вт" to Calendar.TUESDAY,"ср" to Calendar.WEDNESDAY,"чт" to Calendar.THURSDAY,"пт" to Calendar.FRIDAY,"сб" to Calendar.SATURDAY,"вс" to Calendar.SUNDAY);val wanted=map.filterKeys{o.optString("days").lowercase().contains(it)}.values.toSet();if(wanted.isNotEmpty()){var n=0;while(c.get(Calendar.DAY_OF_WEEK) !in wanted&&n++<8)c.add(Calendar.DAY_OF_YEAR,1)}}
        }
        val start=o.optString("windowStart");val end=o.optString("windowEnd")
        if(start.matches(Regex("\\d{2}:\\d{2}"))&&end.matches(Regex("\\d{2}:\\d{2}"))){val a=start.split(":").map{it.toInt()};val b=end.split(":").map{it.toInt()};val mins=c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);val lo=a[0]*60+a[1];val hi=b[0]*60+b[1];if(lo<=hi&&mins !in lo..hi){c.set(Calendar.HOUR_OF_DAY,a[0]);c.set(Calendar.MINUTE,a[1]);if(c.timeInMillis<=now.timeInMillis)c.add(Calendar.DAY_OF_YEAR,1)}}
        return c
    }
}
