package com.nocturne.app

import android.app.*
import android.content.*
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

private fun soundUri(context:Context,tone:String):Uri? = when(tone){
    "builtin:soft" -> Uri.parse("android.resource://${context.packageName}/${com.nocturne.app.R.raw.soft}")
    "builtin:chime" -> Uri.parse("android.resource://${context.packageName}/${com.nocturne.app.R.raw.chime}")
    "default" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    else -> if(tone.startsWith("content://")) Uri.parse(tone) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
}
private fun channelFor(context:Context,tone:String,vibrate:Boolean):String {
    val id="nocturne_${tone.hashCode().toUInt().toString(16)}_${if(vibrate)1 else 0}"
    if(Build.VERSION.SDK_INT>=26){val manager=context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;if(manager.getNotificationChannel(id)==null){val channel=NotificationChannel(id,"Nocturne · ${if(tone.startsWith("builtin:"))"мелодия приложения" else "сигнал"}",NotificationManager.IMPORTANCE_HIGH).apply{description="Будильники и напоминания Nocturne";enableVibration(vibrate);setSound(soundUri(context,tone),null)};manager.createNotificationChannel(channel)}}
    return id
}

class AlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {
        val id=intent.getStringExtra("id")?:return
        val kind=intent.getStringExtra("kind")?:"reminder"
        val prefs=context.getSharedPreferences("nocturne_data",Context.MODE_PRIVATE)
        val arr=runCatching{JSONArray(prefs.getString(kind,"[]"))}.getOrElse{JSONArray()}
        var item:JSONObject?=null
        for(i in 0 until arr.length())if(arr.getJSONObject(i).optString("id")==id){item=arr.getJSONObject(i);break}
        val o=item?:return
        if(!o.optBoolean("enabled",true))return
        val title=o.optString("title","Напоминание");val tone=o.optString("tone","default");val vibrate=o.optBoolean("vibrate",true)
        val channel=channelFor(context,tone,vibrate)
        val doneIntent=Intent(context,ConfirmReceiver::class.java).putExtra("id",id).putExtra("kind",kind)
        val done=PendingIntent.getBroadcast(context,id.hashCode()+100,doneIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snoozeIntent=Intent(context,SnoozeReceiver::class.java).putExtra("id",id).putExtra("kind",kind)
        val snooze=PendingIntent.getBroadcast(context,id.hashCode()+200,snoozeIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder=if(Build.VERSION.SDK_INT>=26)Notification.Builder(context,channel) else Notification.Builder(context).setSound(soundUri(context,tone))
        val notification=builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle(if(kind=="alarm")"Пора просыпаться" else "Nocturne напоминает").setContentText(title).setCategory(if(kind=="alarm")Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER).setAutoCancel(true).addAction(android.R.drawable.ic_lock_idle_alarm,"Через 5 минут",snooze).addAction(android.R.drawable.checkbox_on_background,"Выполнено",done).build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id.hashCode(),notification)
        if(o.optString("repeat")!="Один раз") Scheduler.schedule(context,o)
        else if(o.optBoolean("confirm",false)) {
            val again=Intent(context,AlarmReceiver::class.java).putExtra("id",id).putExtra("kind",kind)
            val pi=PendingIntent.getBroadcast(context,id.hashCode(),again,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val am=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+5*60*1000L,pi) } catch(_:SecurityException) { am.set(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+5*60*1000L,pi) }
        }
    }
}
class ConfirmReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val id=intent.getStringExtra("id")?:return;val kind=intent.getStringExtra("kind")?:"reminder";val p=context.getSharedPreferences("nocturne_data",Context.MODE_PRIVATE);val a=runCatching{JSONArray(p.getString(kind,"[]"))}.getOrElse{JSONArray()};for(i in 0 until a.length()){val o=a.getJSONObject(i);if(o.optString("id")==id){if(o.optString("repeat")=="Один раз"){o.put("enabled",false);Scheduler.cancel(context,id)};p.edit().putString(kind,a.put(i,o).toString()).apply();break}};(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.hashCode())}}
class SnoozeReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){val id=intent.getStringExtra("id")?:return;val kind=intent.getStringExtra("kind")?:"reminder";val p=context.getSharedPreferences("nocturne_data",Context.MODE_PRIVATE);val a=runCatching{JSONArray(p.getString(kind,"[]"))}.getOrElse{JSONArray()};var o:JSONObject?=null;for(i in 0 until a.length())if(a.getJSONObject(i).optString("id")==id)o=a.getJSONObject(i);val item=o?:return;val at=System.currentTimeMillis()+5*60*1000L;val pi=PendingIntent.getBroadcast(context,id.hashCode(),Intent(context,AlarmReceiver::class.java).putExtra("id",id).putExtra("kind",kind),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val am=context.getSystemService(Context.ALARM_SERVICE) as AlarmManager;try{am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)}catch(_:SecurityException){am.set(AlarmManager.RTC_WAKEUP,at,pi)}}}
class BootReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){if(intent.action!=Intent.ACTION_BOOT_COMPLETED&&intent.action!="android.intent.action.MY_PACKAGE_REPLACED")return;val p=context.getSharedPreferences("nocturne_data",Context.MODE_PRIVATE);listOf("alarm","reminder").forEach{k->val a=runCatching{JSONArray(p.getString(k,"[]"))}.getOrElse{JSONArray()};for(i in 0 until a.length())Scheduler.schedule(context,a.getJSONObject(i))}}}
