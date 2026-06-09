package io.github.karino2.pngnote.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.util.concurrent.Executors

class BitmapActor {
    private val executor = Executors.newSingleThreadExecutor()

    var bitmap: Bitmap? = null
        private set
    var bmpCanvas: Canvas? = null
        private set

    fun ensureBitmap(width: Int, height: Int): Pair<Bitmap, Canvas> {
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
            bmpCanvas = Canvas(bitmap!!)
        }
        return Pair(bitmap!!, bmpCanvas!!)
    }

    fun post(action: (Bitmap, Canvas) -> Unit) {
        executor.execute {
            val (bmp, canvas) = synchronized(this) {
                // We assume ensureBitmap is called before any post actions that need them, 
                // or the action handles nulls. But for now, let's just provide them if available.
                if (bitmap == null) return@execute
                Pair(bitmap!!, bmpCanvas!!)
            }
            action(bmp, canvas)
        }
    }
    
    // For cases where we need to ensure bitmap is created on the actor thread or before posting
    fun postWithEnsure(width: Int, height: Int, action: (Bitmap, Canvas) -> Unit) {
        executor.execute {
            val (bmp, canvas) = synchronized(this) {
                ensureBitmap(width, height)
            }
            action(bmp, canvas)
        }
    }
}
