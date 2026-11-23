package com.pruffyfy.jarvis

import android.app.*
import android.os.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var status: TextView
    private val prefs by lazy { getSharedPreferences("jarvis", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(state: Bundle?) { super.onCreate(state); buildUi() }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(22,18,22,14); setBackgroundColor(Color.rgb(14,16,22))
        }
        root.addView(TextView(this).apply {
            text="JARVIS"; textSize=28f; setTextColor(Color.WHITE); typeface=android.graphics.Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(-1,48))
        status=TextView(this).apply { text="Локальный режим • готов"; textSize=13f; setTextColor(Color.rgb(150,160,175)) }
        root.addView(status,LinearLayout.LayoutParams(-1,30))
        messages=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(messages) },LinearLayout.LayoutParams(-1,0,1f))
        val row=LinearLayout(this)
        input=EditText(this).apply {
            hint="Команда Jarvis…"; setHintTextColor(Color.rgb(120,125,135)); setTextColor(Color.WHITE)
            setSingleLine(false); maxLines=4; setPadding(20,10,20,10); background=round(Color.rgb(27,30,39),28)
        }
        row.addView(input,LinearLayout.LayoutParams(0,58,1f))
        row.addView(Button(this).apply {
            text="➤"; textSize=20f; setTextColor(Color.WHITE); background=round(Color.rgb(48,104,255),28)
            setOnClickListener { sendMessage() }
        },LinearLayout.LayoutParams(62,58).apply{leftMargin=10})
        root.addView(row)
        root.addView(Button(this).apply {
            text="Настройки моделей"; setTextColor(Color.LTGRAY); background=round(Color.rgb(27,30,39),22)
            setOnClickListener { showSettings() }
        },LinearLayout.LayoutParams(-1,52).apply{topMargin=10})
        setContentView(root)
        addMessage("Jarvis","Готов. Без настроенного API я не выдаю локальный ответ за ответ нейросети.")
    }

    private fun sendMessage() {
        val prompt=input.text.toString().trim(); if(prompt.isEmpty()) return
        addMessage("Вы",prompt); input.text.clear()
        val endpoint=prefs.getString("endpoint","") ?: ""
        val key=prefs.getString("api_key","") ?: ""
        val model=prefs.getString("model","") ?: ""
        if(endpoint.isBlank() || key.isBlank() || model.isBlank()) {
            addMessage("Jarvis","Настрой endpoint, модель и API-ключ. Затем запрос пойдёт через настроенный API.")
            return
        }
        status.text="Запрос: $model"
        executor.execute {
            val answer=callApi(endpoint,key,model,prompt)
            runOnUiThread { status.text="Готов"; addMessage("Jarvis",answer) }
        }
    }

    private fun callApi(endpoint:String,key:String,model:String,prompt:String):String=try {
        val c=(URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod="POST"; connectTimeout=15000; readTimeout=60000; doOutput=true
            setRequestProperty("Authorization","Bearer $key"); setRequestProperty("Content-Type","application/json")
        }
        val body=JSONObject().apply {
            put("model",model)
            put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt)))
        }.toString()
        c.outputStream.use{it.write(body.toByteArray())}
        val stream=if(c.responseCode in 200..299)c.inputStream else c.errorStream
        val response=stream.bufferedReader().use{it.readText()}
        if(c.responseCode !in 200..299) "API ошибка " + c.responseCode + ": " + response
        else JSONObject(response).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: "Ответ получен, но его формат не распознан."
    }catch(e:Exception){"Ошибка соединения: " + (e.message ?: "неизвестная ошибка")}

    private fun showSettings() {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(30,10,30,10)}
        val endpoint=EditText(this).apply{hint="API endpoint";setText(prefs.getString("endpoint",""))}
        val model=EditText(this).apply{hint="Модель";setText(prefs.getString("model",""))}
        val key=EditText(this).apply{hint="API key";setText(prefs.getString("api_key",""));inputType=0x00000081}
        box.addView(endpoint);box.addView(model);box.addView(key)
        AlertDialog.Builder(this).setTitle("Провайдер").setView(box)
            .setPositiveButton("Сохранить"){_,_->prefs.edit().putString("endpoint",endpoint.text.toString().trim()).putString("model",model.text.toString().trim()).putString("api_key",key.text.toString()).apply();status.text="Настройки сохранены"}
            .setNegativeButton("Отмена",null).show()
    }

    private fun addMessage(who:String,text:String){
        messages.addView(TextView(this).apply{
            this.text=who + "\n" + text;textSize=15f;setTextColor(Color.WHITE);setPadding(18,14,18,14);background=round(Color.rgb(24,27,35),20)
        },LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=10})
    }
    private fun round(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=radius.toFloat()}
    override fun onDestroy(){executor.shutdownNow();super.onDestroy()}
}
