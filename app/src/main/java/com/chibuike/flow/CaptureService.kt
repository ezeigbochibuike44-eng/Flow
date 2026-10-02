package com.chibuike.flow

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Grabs ONE frame of the screen, returns it through Bus, then stops. */
class CaptureService : Service() {
    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("cap", "Capture", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "cap")
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("Reading your screen").build()
        startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val data: Intent = (if (android.os.Build.VERSION.SDK_INT >= 33)
            i?.getParcelableExtra("data", Intent::class.java)
        else @Suppress("DEPRECATION") i?.getParcelableExtra<Intent>("data")) ?: run { stopSelf(); return START_NOT_STICKY }
        val mp = getSystemService(MediaProjectionManager::class.java)
            .getMediaProjection(i!!.getIntExtra("code", 0), data)
        val h = Handler(Looper.getMainLooper())
        mp.registerCallback(object : MediaProjection.Callback() {}, h)
        h.postDelayed({ grab(mp, h) }, 700) // let the permission dialog disappear first
        return START_NOT_STICKY
    }

    private fun grab(mp: MediaProjection, h: Handler) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels; val hh = dm.heightPixels
        val reader = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 2)
        val vd = mp.createVirtualDisplay("fv", w, hh, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, h)
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            r.setOnImageAvailableListener(null, null)
            val pl = img.planes[0]
            val pad = pl.rowStride - pl.pixelStride * w
            val raw = Bitmap.createBitmap(w + pad / pl.pixelStride, hh, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(pl.buffer)
            img.close()
            val out = Bitmap.createBitmap(raw, 0, 0, w, hh)
            vd.release(); reader.close(); mp.stop(); stopSelf()
            Bus.onShot?.invoke(out)
        }, h)
    }
}
