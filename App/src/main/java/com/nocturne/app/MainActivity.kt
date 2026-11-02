package com.nocturne.app

import android.app.*
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("nocturne_data", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private var selectedTab = "Сегодня"
    private var pendingToneFor = ""
    private var chosenTone = ""
    private var chosenToneName = ""
    private val bg = Color.rgb(19, 23, 40)
    private val panel = Color.rgb(31, 37, 59)
    private val panel2 = Color.rgb(39, 45, 69)
    private val accent = Color.rgb(190, 180, 231)
    private val pale = Color.rgb(224, 222, 242)
    private val muted = Color.rgb(157, 165, 192)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createChannel()
        startNocturneService()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        setContentView(scroll)
        render()
        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) AlertDialog.Builder(this).setTitle("Точные будильники").setMessage("Разрешите Nocturne устанавливать точные будильники в системных настройках, чтобы сигналы срабатывали вовремя.").setPositiveButton("Настройки") { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }.setNegativeButton("Позже", null).show()
        }
    }

    override fun onResume() { super.onResume(); startNocturneService() }

    private fun startNocturneService() {
        val i = Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_UPDATE)
        try { if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i) } catch (_: Exception) { }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(s: String, size: Float, color: Int = pale, bold: Boolean = false) = TextView(this).apply { text=s; textSize=size; setTextColor(color); if(bold) setTypeface(null,1); setPadding(0,dp(4),0,dp(4)) }
    private fun button(s: String, action: () -> Unit, primary: Boolean = false): TextView = TextView(this).apply {
        text=s; textSize=14f; gravity=Gravity.CENTER; setTextColor(if(primary) bg else pale); setPadding(dp(15),dp(12),dp(15),dp(12));
        background=android.graphics.drawable.GradientDrawable().apply { cornerRadius=dp(16).toFloat(); setColor(if(primary) accent else panel2) }
        setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(13),dp(16),dp(13)); background=android.graphics.drawable.GradientDrawable().apply { cornerRadius=dp(20).toFloat(); setColor(panel) } }
    private fun space(h:Int=10) = View(this).apply { layoutParams=LinearLayout.LayoutParams(1,dp(h)) }
    private fun addFull(v:View, bottom:Int=0) { root.addView(v,LinearLayout.LayoutParams(-1,-2).apply { leftMargin=dp(18); rightMargin=dp(18); bottomMargin=dp(bottom) }) }
    private fun header(title:String, subtitle:String="") {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(22),dp(25),dp(22),dp(16)) }
        box.addView(label("NOCTURNE  ✦",12f,accent,true)); box.addView(label(title,27f,pale,true)); if(subtitle.isNotBlank()) box.addView(label(subtitle,13f,muted))
        root.addView(box)
    }
    private fun nav() {
        val bar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; setPadding(dp(28),dp(8),dp(28),dp(12)) }
        listOf("Сегодня","Напоминания").forEach { t ->
            val b=TextView(this).apply { text=t; textSize=13f; gravity=Gravity.CENTER; setTextColor(if(selectedTab==t) accent else muted); setPadding(dp(12),dp(12),dp(12),dp(12)); setOnClickListener { selectedTab=t; render() } }
            bar.addView(b,LinearLayout.LayoutParams(0,-2,1f))
        }
        root.addView(bar)
    }
    private fun render() {
        root.removeAllViews()
        if(selectedTab=="Напоминания") {
            header("Напоминания","Ваше расписание и важные сигналы")
            addFull(button("＋  Добавить напоминание",{ editSchedule("alarm") },true),12)
            renderAllSchedules()
        } else home()
        nav()
    }
    private fun home() {
        val date=SimpleDateFormat("EEEE, d MMMM",Locale("ru")).format(Date()).replaceFirstChar { it.uppercase() }
        header("Тихий ритм дня",date)
        val hero=card(); hero.addView(label("ДОБРО ПОЖАЛОВАТЬ",11f,accent,true)); hero.addView(label(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date()),38f,pale,true)); hero.addView(label("Все важное - в своём ритме.",14f,muted)); addFull(hero,12)
        addFull(button("＋  Новое напоминание",{selectedTab="Напоминания";render()},true),16)
        addFull(label("Ближайшее",19f,pale,true),6)
        val upcoming=allSchedules().filter { it.optBoolean("enabled",true) }.sortedBy { it.optString("nextLabel") }.take(5)
        if(upcoming.isEmpty()) { val c=card(); c.addView(label("Пока ничего не запланировано",15f,pale)); c.addView(label("Добавьте напоминание - здесь появится ваше расписание.",12f,muted)); addFull(c) }
        else upcoming.forEach { addScheduleCard(it) }
    }
    private fun renderAllSchedules() {
        val all=allSchedules()
        if(all.isEmpty()) { val c=card(); c.addView(label("Здесь пока пусто",16f)); c.addView(label("Создайте первое напоминание - оно появится в этом списке.",12f,muted)); addFull(c) }
        all.forEach { addScheduleCard(it) }
    }
    private fun addScheduleCard(o:JSONObject) {
        val c=card(); val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val info=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        info.addView(label(o.optString("time","09:00"),25f,accent,true)); info.addView(label(o.optString("title","Событие"),15f,pale,true)); info.addView(label(cycleText(o),11f,muted)); if(o.optString("note").isNotBlank()) info.addView(label(o.optString("note"),12f,muted));
        row.addView(info,LinearLayout.LayoutParams(0,-2,1f))
        val toggle=Switch(this).apply { isChecked=o.optBoolean("enabled",true); buttonTintList=null; setOnCheckedChangeListener { _, checked -> o.put("enabled",checked); save(o.optString("kind"),replace(o.optString("id"),o)); if(checked) Scheduler.schedule(this@MainActivity,o) else Scheduler.cancel(this@MainActivity,o.optString("id")); startNocturneService() } }
        row.addView(toggle); c.addView(row)
        c.addView(space(8)); val tools=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        tools.addView(button("Изменить",{editSchedule(o.optString("kind"),o)},false),LinearLayout.LayoutParams(0,-2,1f).apply { rightMargin=dp(5) })
        tools.addView(button("Удалить",{ AlertDialog.Builder(this@MainActivity).setMessage("Удалить эту запись?").setNegativeButton("Отмена",null).setPositiveButton("Удалить"){_,_->Scheduler.cancel(this@MainActivity,o.optString("id")); remove(o.optString("kind"),o.optString("id"));startNocturneService();render()}.show() },false),LinearLayout.LayoutParams(0,-2,1f).apply { leftMargin=dp(5) })
        c.addView(tools); addFull(c,9)
    }
    private fun editSchedule(kind:String, existing:JSONObject?=null) {
        val o=existing ?: JSONObject()
        chosenTone=o.optString("tone", "builtin:soft")
        chosenToneName=o.optString("toneName", "Мягкая мелодия Nocturne")
        fun field(caption:String, description:String, input:View):LinearLayout = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            addView(label(caption,14f,pale,true))
            addView(label(description,11f,muted))
            addView(input)
            setPadding(0,dp(4),0,dp(9))
        }
        val title=EditText(this).apply { hint="Например: Принять лекарство"; setSingleLine(); setText(o.optString("title")) ; setTextColor(pale); setHintTextColor(muted) }
        val time=EditText(this).apply { hint="ЧЧ:ММ"; setSingleLine(); inputType=android.text.InputType.TYPE_CLASS_DATETIME or android.text.InputType.TYPE_DATETIME_VARIATION_TIME; filters=arrayOf(android.text.InputFilter.LengthFilter(5)); setText(o.optString("time","09:00")); setTextColor(pale); setHintTextColor(muted) }
        time.setOnClickListener { val parts=time.text.toString().split(":"); val h=parts.getOrNull(0)?.toIntOrNull()?:9; val m=parts.getOrNull(1)?.toIntOrNull()?:0; TimePickerDialog(this,{_,hour,minute->time.setText(String.format(Locale.US,"%02d:%02d",hour,minute))},h,m,true).show() }
        time.addTextChangedListener(object : android.text.TextWatcher {
            private var editing=false
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){}
            override fun afterTextChanged(e:android.text.Editable?) { if(editing||e==null)return; val digits=e.toString().filter{it.isDigit()}.take(4); val formatted=if(digits.length>2)digits.substring(0,2)+":"+digits.substring(2) else digits; if(formatted!=e.toString()){editing=true;e.replace(0,e.length,formatted);editing=false} }
        })
        val repeatOptions=listOf("Один раз","Каждый день","По будням","По выходным","Выбранные дни недели","Каждую неделю","Каждый месяц","Каждый год","Каждые N дней","Каждые N часов")
        val repeat=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,repeatOptions); setSelection(repeatOptions.indexOf(o.optString("repeat")).coerceAtLeast(0)) }
        val interval=EditText(this).apply { hint="Например, 3"; inputType=2; setText(o.optString("interval","1")); setTextColor(pale); setHintTextColor(muted) }
        val selectedDays=mutableSetOf<String>().apply { val saved=o.optString("days","пн,вт,ср,чт,пт").split(",").map{it.trim()}; addAll(saved.filter{it.isNotBlank()}) }
        val dayNames=listOf("пн","вт","ср","чт","пт","сб","вс")
        val daysButton=button("Выбрать дни недели  ·  ${selectedDays.sorted().joinToString(", ").ifBlank { "не выбраны" }}",{},false)
        fun updateDays(){daysButton.text="Выбрать дни недели  ·  ${dayNames.filter{it in selectedDays}.joinToString(", ").ifBlank { "не выбраны" }}"}
        daysButton.setOnClickListener {
            val checks=dayNames.map { day -> CheckBox(this).apply { text=day.uppercase(Locale("ru")); setTextColor(pale); isChecked=day in selectedDays } }
            val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(8)); checks.forEach{addView(it)} }
            AlertDialog.Builder(this).setTitle("Дни недели").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Готово"){_,_->selectedDays.clear();checks.forEachIndexed{i,c->if(c.isChecked)selectedDays.add(dayNames[i])};updateDays()}.show()
        }
        val date=EditText(this).apply { hint="Выберите дату"; isFocusable=false; setText(o.optString("date",SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date()))); setTextColor(pale); setHintTextColor(muted) }
        date.setOnClickListener { val cal=Calendar.getInstance(); val old=runCatching{SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(date.text.toString())}.getOrNull();if(old!=null)cal.time=old;DatePickerDialog(this,{_,y,m,d->date.setText(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d))},cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show() }
        val note=EditText(this).apply { hint="Дополнительная заметка (необязательно)"; minLines=2; gravity=Gravity.TOP; setText(o.optString("note")); setTextColor(pale); setHintTextColor(muted) }
        val tone=TextView(this).apply { text="♫  ${o.optString("toneName","Мягкая мелодия Nocturne")}  · изменить"; setTextColor(accent); textSize=14f; setPadding(0,dp(12),0,dp(12)); setOnClickListener { pendingToneFor="${kind}:${o.optString("id","")}"; AlertDialog.Builder(this@MainActivity).setTitle("Мелодия сигнала").setItems(arrayOf("Мягкая мелодия Nocturne","Хрустальный звон","Системный звук будильника","Выбрать файл на телефоне")){_,which->when(which){0->{chosenTone="builtin:soft";chosenToneName="Мягкая мелодия Nocturne"};1->{chosenTone="builtin:chime";chosenToneName="Хрустальный звон"};2->{chosenTone="default";chosenToneName="Системный звук будильника"};3->{val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="audio/*"};startActivityForResult(intent,42)}};text="♫  $chosenToneName  · изменить"}.show() } }
        val repeatConfirm=CheckBox(this).apply { text="Повторять сигнал, пока не подтвержу"; setTextColor(pale); isChecked=o.optBoolean("confirm",false) }
        val vibrate=CheckBox(this).apply { text="Вибрация"; setTextColor(pale); isChecked=o.optBoolean("vibrate",true) }
        val volumeMode=RadioGroup(this).apply { orientation=RadioGroup.VERTICAL; val ramp=RadioButton(this@MainActivity).apply{text="Плавное нарастание громкости";setTextColor(pale);id=1};val full=RadioButton(this@MainActivity).apply{text="Сразу на полную громкость";setTextColor(pale);id=2};addView(ramp);addView(full);check(if(o.optString("volumeMode","ramp")=="full")2 else 1) }
        val intervalField=field("Интервал повторения","Укажите число дней или часов для выбранного режима.",interval)
        val daysField=field("Дни недели","Отметьте дни, в которые нужно повторять напоминание.",daysButton)
        val form=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(8),dp(20),dp(8)) }
        form.addView(field("Название","Коротко укажите, о чём нужно напомнить.",title))
        form.addView(field("Время","Укажите время срабатывания. Можно нажать на поле и выбрать время.",time))
        form.addView(field("Повторение","Выберите, как часто должно срабатывать напоминание.",repeat))
        form.addView(intervalField); form.addView(daysField)
        form.addView(field("Дата начала","Выберите дату первого напоминания в календаре.",date))
        form.addView(field("Заметка","Дополнительная информация, которая поможет не забыть детали.",note))
        form.addView(label("Мелодия",14f,pale,true)); form.addView(tone)
        form.addView(repeatConfirm); form.addView(vibrate); form.addView(label("Громкость сигнала",14f,accent,true)); form.addView(volumeMode)
        fun refreshVisibility(){val r=repeat.selectedItem?.toString().orEmpty();intervalField.visibility=if(r.contains("N дней")||r.contains("N часов"))View.VISIBLE else View.GONE;daysField.visibility=if(r=="Выбранные дни недели")View.VISIBLE else View.GONE}
        repeat.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(parent:android.widget.AdapterView<*>?){};override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){refreshVisibility()}}
        refreshVisibility()
        val scroll=ScrollView(this).apply { addView(form); isFillViewport=true }
        val dialog=AlertDialog.Builder(this).setTitle("Настройка напоминания").setView(scroll).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply{cornerRadius=dp(22).toFloat();setColor(Color.rgb(42,48,68))})
            dialog.window?.setLayout((resources.displayMetrics.widthPixels-dp(36)).coerceAtLeast(dp(280)),-2)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val tm=time.text.toString().trim();val parts=tm.split(":")
                if(title.text.isBlank()||parts.size!=2||runCatching{parts[0].toInt() !in 0..23||parts[1].toInt() !in 0..59}.getOrDefault(true)){Toast.makeText(this,"Проверьте название и время (ЧЧ:ММ)",Toast.LENGTH_LONG).show();return@setOnClickListener}
                if(repeat.selectedItem.toString()=="Выбранные дни недели"&&selectedDays.isEmpty()){Toast.makeText(this,"Выберите хотя бы один день недели",Toast.LENGTH_LONG).show();return@setOnClickListener}
                val id=o.optString("id",UUID.randomUUID().toString())
                val updated=JSONObject().apply{put("id",id);put("kind",kind);put("title",title.text.toString());put("time",tm);put("repeat",repeat.selectedItem.toString());put("interval",interval.text.toString().toIntOrNull()?.coerceAtLeast(1)?:1);put("days",dayNames.filter{it in selectedDays}.joinToString(","));put("date",date.text.toString());put("note",note.text.toString());put("tone",chosenTone.ifBlank{"builtin:soft"});put("toneName",chosenToneName);put("confirm",repeatConfirm.isChecked);put("vibrate",vibrate.isChecked);put("volumeMode",if(volumeMode.checkedRadioButtonId==2)"full" else "ramp");put("enabled",true)}
                save(kind,replace(id,updated));Scheduler.cancel(this,id);Scheduler.schedule(this,updated);startNocturneService();dialog.dismiss();selectedTab="Напоминания";render()
            }
        }
        dialog.show()
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data); if(requestCode==42&&resultCode==RESULT_OK){val uri=data?.data?:return; chosenTone=uri.toString(); chosenToneName=displayName(uri); try{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}; Toast.makeText(this,"Выбрана мелодия: $chosenToneName. Нажмите «Сохранить» в форме.",Toast.LENGTH_LONG).show() } }
    private fun displayName(uri:Uri):String { var n="Своя мелодия"; contentResolver.query(uri,null,null,null,null)?.use{if(it.moveToFirst()){val i=it.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0)n=it.getString(i)}};return n }
    private fun cycleText(o:JSONObject):String = o.optString("repeat")+if(o.optString("repeat").contains("N дней")||o.optString("repeat").contains("N часов")) " · интервал ${o.optInt("interval",1)}" else ""
    private fun load(k:String):JSONArray=runCatching{JSONArray(prefs.getString(k,"[]"))}.getOrElse{JSONArray()}
    private fun save(k:String,a:JSONArray){prefs.edit().putString(k,a.toString()).apply()}
    private fun replace(id:String,o:JSONObject):JSONArray { val a=load(o.optString("kind")); for(i in 0 until a.length())if(a.getJSONObject(i).optString("id")==id){a.put(i,o);return a};a.put(o);return a }
    private fun remove(k:String,id:String){val a=load(k);val b=JSONArray();for(i in 0 until a.length())if(a.getJSONObject(i).optString("id")!=id)b.put(a.getJSONObject(i));save(k,b)}
    private fun allSchedules(): List<JSONObject> = (listOf("alarm","reminder").flatMap{k->val a=load(k);(0 until a.length()).map{a.getJSONObject(it)}})
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("nocturne_alerts", "Nocturne: сигналы", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Напоминания"
                enableVibration(true)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
