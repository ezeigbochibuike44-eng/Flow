package com.chibuike.flow

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        val p = getSharedPreferences("f", MODE_PRIVATE)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad)
        }
        fun tv(t: String, s: Float) = TextView(this).apply { text = t; textSize = s }
        fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; setOnClickListener { a() } }
        val key = EditText(this).apply {
            hint = "Anthropic API key (for Screen AI)"; setText(p.getString("key", ""))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val model = EditText(this).apply { setText(p.getString("model", "claude-sonnet-5-5")) }
        col.addView(tv("Flow", 28f))
        col.addView(tv("Floating volume button + Screen AI + tools", 14f))
        col.addView(key); col.addView(model)
        col.addView(btn("Save settings") {
            p.edit().putString("key", key.text.toString().trim())
                .putString("model", model.text.toString().trim()).apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })
        col.addView(btn("1. Allow display over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        col.addView(btn("2. Start floating button") {
            if (!Settings.canDrawOverlays(this))
                Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
            else startForegroundService(Intent(this, FloatingService::class.java))
        })
        col.addView(btn("Stop") { stopService(Intent(this, FloatingService::class.java)) })
        col.addView(tv("\nBuilt by Chibuike", 12f))
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
