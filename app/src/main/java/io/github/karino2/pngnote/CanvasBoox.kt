package io.github.karino2.pngnote

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList


class CanvasBoox(context: Context, var initialBmp: Bitmap? = null) : SurfaceView(context) {
    /*
    lateinit var bitmap: Bitmap
    private lateinit var bmpCanvas: Canvas
    private var clearCount = 0

    private val bmpPaint = Paint(Paint.DITHER_FLAG)
     */

    private var initCount = 0

    private val inputCallback = object: RawInputCallback() {
        override fun onBeginRawDrawing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onRawDrawingTouchPointMoveReceived(p0: TouchPoint?) {
        }

        override fun onRawDrawingTouchPointListReceived(p0: TouchPointList?) {
        }

        override fun onBeginRawErasing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onEndRawErasing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onRawErasingTouchPointMoveReceived(p0: TouchPoint?) {
        }

        override fun onRawErasingTouchPointListReceived(p0: TouchPointList?) {
        }

    }

    private val touchHelper by lazy { TouchHelper.create(this, inputCallback) }
    private val layoutChangedListener : View.OnLayoutChangeListener by lazy {
        OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->

            /**
             * Called when the layout bounds of a view changes due to layout processing.
             *
             * @param v The view whose bounds have changed.
             * @param left The new value of the view's left property.
             * @param top The new value of the view's top property.
             * @param right The new value of the view's right property.
             * @param bottom The new value of the view's bottom property.
             * @param oldLeft The previous value of the view's left property.
             * @param oldTop The previous value of the view's top property.
             * @param oldRight The previous value of the view's right property.
             * @param oldBottom The previous value of the view's bottom property.
             */
            /**
             * Called when the layout bounds of a view changes due to layout processing.
             *
             * @param v The view whose bounds have changed.
             * @param left The new value of the view's left property.
             * @param top The new value of the view's top property.
             * @param right The new value of the view's right property.
             * @param bottom The new value of the view's bottom property.
             * @param oldLeft The previous value of the view's left property.
             * @param oldTop The previous value of the view's top property.
             * @param oldRight The previous value of the view's right property.
             * @param oldBottom The previous value of the view's bottom property.
             */
            if(cleanSurfaceView()) {
                removeOnLayoutChangeListener(layoutChangedListener)
            }

            val exclude = emptyList<Rect>()
            val limit = Rect()
            getLocalVisibleRect(limit)
            touchHelper.setStrokeWidth(3f)
                .setLimitRect(limit, exclude)
                .openRawDrawing()
            touchHelper.setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
        }
    }

    private val surfaceCallback : SurfaceHolder.Callback by lazy { object : SurfaceHolder.Callback {
        /**
         * This is called immediately after the surface is first created.
         * Implementations of this should start up whatever rendering code
         * they desire.  Note that only one thread can ever draw into
         * a [Surface], so you should not draw into the Surface here
         * if your normal rendering will be in another thread.
         *
         * @param holder The SurfaceHolder whose surface is being created.
         */
        override fun surfaceCreated(holder: SurfaceHolder) {
            cleanSurfaceView()
        }

        /**
         * This is called immediately after any structural changes (format or
         * size) have been made to the surface.  You should at this point update
         * the imagery in the surface.  This method is always called at least
         * once, after [.surfaceCreated].
         *
         * @param holder The SurfaceHolder whose surface has changed.
         * @param format The new [PixelFormat] of the surface.
         * @param width The new width of the surface.
         * @param height The new height of the surface.
         */
        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        }

        /**
         * This is called immediately before a surface is being destroyed. After
         * returning from this call, you should no longer try to access this
         * surface.  If you have a rendering thread that directly accesses
         * the surface, you must ensure that thread is no longer touching the
         * Surface before returning from this function.
         *
         * @param holder The SurfaceHolder whose surface is being destroyed.
         */
        override fun surfaceDestroyed(holder: SurfaceHolder) {
            holder.removeCallback(surfaceCallback)
        }

    } }

    fun firstInit() {
        touchHelper
        addOnLayoutChangeListener(layoutChangedListener)
        holder.addCallback(surfaceCallback)
    }

    fun ensureInit(callCount: Int) {
        if(callCount == 1 && initCount != 1) {
            initCount = 1
            startPenRender()
        }
    }

    fun startPenRender() {
        touchHelper.setRawDrawingEnabled(true)
        touchHelper.isRawDrawingRenderEnabled = true
    }

    private fun cleanSurfaceView(): Boolean {
        if (holder == null) {
            return false
        }
        val canvas: Canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        holder.unlockCanvasAndPost(canvas)
        return true
    }
}