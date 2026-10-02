package com.chibuike.flow

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/** Invisible activity that asks for screen-capture permission, then hands off to CaptureService. */
class CaptureActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val m = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(m.createScreenCaptureIntent(), 1)
    }
    override fun onActivityResult(r: Int, code: Int, data: Intent?) {
        if (code == RESULT_OK && data != null) {
            startForegroundService(Intent(this, CaptureService::class.java)
                .putExtra("code", code).putExtra("data", data))
        } else Bus.onShot?.invoke(null)
        finish()
    }
}
