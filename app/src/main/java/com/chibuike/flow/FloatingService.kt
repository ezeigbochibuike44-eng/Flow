package com.chibuike.flow

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var am: AudioManager
    private lateinit var root: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var lp: WindowManager.LayoutParams
    private var card: View? = null
    private var cardText: TextView? = null
    private var torch = false
    private val ui = Handler(Looper.getMainLooper())
    private var d = 1f

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("f", "Flow", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "f")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setContentTitle("Flow is running").build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        am = getSystemService(AudioManager::class.java)
        d = resources.displayMetrics.density
        build()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun pill(t: String, a: () -> Unit) = TextView(this).apply {
        text = t; textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setPadding((14 * d).toInt(), (9 * d).toInt(), (14 * d).toInt(), (9 * d).toInt())
        background = GradientDrawable().apply { cornerRadius = 22 * d; setColor(0xEE222831.toInt()) }
        setOnClickListener { a() }
    }

    private fun build() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val bubble = TextView(this).apply {
            text = "🔊"; textSize = 22f; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xDD1E88E5.toInt()) }
            layoutParams = LinearLayout.LayoutParams((54 * d).toInt(), (54 * d).toInt())
        }
        fun add(t: String, a: () -> Unit) {
            panel.addView(pill(t, a), LinearLayout.LayoutParams(-2, -2).apply { topMargin = (6 * d).toInt() })
        }
        add("🔊  Volume +") { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
        add("🔉  Volume −") { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
        add("🔇  Mute / Unmute") { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI) }
        add("🔔  Ringer mode") { cycleRinger() }
        add("🔦  Flashlight") { toggleTorch() }
        add("🧠  AI: Explain screen") { ai("Look at this screenshot. Briefly explain what is on screen and anything I should know or do next.") }
        add("🌍  AI: Translate screen") { ai("Translate all visible text in this screenshot into English. Keep it short and organised.") }
        add("💬  AI: Suggest reply") { ai("If this screenshot shows a conversation, write 3 short reply options I could send. Otherwise say what is shown.") }
        add("✕  Stop Flow") { stopSelf() }
        root.addView(bubble); root.addView(panel)

        lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = 0; y = (200 * d).toInt()
        }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) + abs(dy) > 15) moved = true
                    if (moved) { lp.x = ox + dx.toInt(); lp.y = oy + dy.toInt(); wm.updateViewLayout(root, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved)
                    panel.visibility = if (panel.visibility == View.GONE) View.VISIBLE else View.GONE
            }
            true
        }
        wm.addView(root, lp)
    }

    private fun cycleRinger() {
        try {
            am.ringerMode = when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            toast(when (am.ringerMode) { 2 -> "Ring"; 1 -> "Vibrate"; else -> "Silent" })
        } catch (e: SecurityException) {
            toast("Allow Do Not Disturb access for Flow in Settings")
        }
    }

    private fun toggleTorch() {
        try {
            val cm = getSystemService(CameraManager::class.java)
            val id = cm.cameraIdList.first { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            torch = !torch; cm.setTorchMode(id, torch)
        } catch (e: Exception) { toast("No flashlight available") }
    }

    // ---------- Screen AI ----------
    private fun ai(prompt: String) {
        val p = getSharedPreferences("f", MODE_PRIVATE)
        val key = p.getString("key", "") ?: ""
        if (key.isBlank()) { toast("Add your API key in the Flow app"); return }
        val model = p.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5"
        panel.visibility = View.GONE
        root.visibility = View.GONE
        Bus.onShot = { bmp ->
            ui.post {
                Bus.onShot = null
                if (bmp == null) { root.visibility = View.VISIBLE; toast("Capture cancelled") }
                else {
                    showCard("Thinking…")
                    Thread {
                        val r = try { callApi(key, model, prompt, bmp) } catch (e: Exception) { "Error: ${e.message}" }
                        ui.post { showCard(r) }
                    }.start()
                }
            }
        }
        startActivity(Intent(this, CaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun callApi(key: String, model: String, prompt: String, bmp: Bitmap): String {
        val s = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
        val sm = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
        val bo = ByteArrayOutputStream(); sm.compress(Bitmap.CompressFormat.JPEG, 80, bo)
        val b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP)
        val img = JSONObject().put("type", "image").put("source", JSONObject()
            .put("type", "base64").put("media_type", "image/jpeg").put("data", b64))
        val txt = JSONObject().put("type", "text").put("text", prompt)
        val body = JSONObject().put("model", model).put("max_tokens", 800).put("messages",
            JSONArray().put(JSONObject().put("role", "user").put("content", JSONArray().put(img).put(txt))))
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 20000; c.readTimeout = 60000
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-api-key", key)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val res = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code >= 400) return "API error $code: $res"
        val arr = JSONObject(res).getJSONArray("content")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
            .filter { it.optString("type") == "text" }.joinToString("\n") { it.getString("text") }
    }

    private fun showCard(text: String) {
        if (card != null) { cardText?.text = text; return }
        val tv = TextView(this).apply { this.text = text; textSize = 15f; setTextColor(Color.WHITE); setTextIsSelectable(false) }
        val sv = ScrollView(this).apply { addView(tv) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(pill("Copy") {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("ai", tv.text))
                toast("Copied")
            })
            addView(pill("Close") { closeCard() }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = (8 * d).toInt() })
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 20 * d; setColor(0xF2111820.toInt()) }
            addView(sv, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.45f).toInt()))
            addView(bar)
        }
        val p = WindowManager.LayoutParams((resources.displayMetrics.widthPixels * 0.92f).toInt(), -2,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        wm.addView(box, p); card = box; cardText = tv
    }

    private fun closeCard() {
        card?.let { wm.removeView(it) }; card = null; cardText = null
        root.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        Bus.onShot = null
        card?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        try { wm.removeView(root) } catch (_: Exception) {}
        super.onDestroy()
    }
}
