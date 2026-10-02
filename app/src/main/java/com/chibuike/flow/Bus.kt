package com.chibuike.flow

import android.graphics.Bitmap

/** Hands a captured screenshot from CaptureService back to FloatingService. */
object Bus { var onShot: ((Bitmap?) -> Unit)? = null }
