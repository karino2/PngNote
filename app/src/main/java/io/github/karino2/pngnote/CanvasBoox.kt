package io.github.karino2.pngnote

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.View.OnLayoutChangeListener
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList


class CanvasBoox(context: Context, var initialBmp: Bitmap? = null) : SurfaceView(context) {
    var bitmap: Bitmap? = null
    private var bmpCanvas: Canvas? = null
    private var clearCount = 0

    private val pencilWidth = 3f
    private val eraserWidth = 50f

    private val bmpPaint = Paint(Paint.DITHER_FLAG)
    private val pathPaint = Paint().apply {
        isAntiAlias = true
        // isDither = true
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        // strokeJoin = Paint.Join.ROUND
        // strokeCap = Paint.Cap.ROUND
        strokeWidth = pencilWidth
    }

    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = eraserWidth
    }



    private var initCount = 0

    private val inputCallback = object: RawInputCallback() {
        override fun onBeginRawDrawing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onRawDrawingTouchPointMoveReceived(p0: TouchPoint?) {
        }

        override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
            drawPointsToBitmap(plist.points)
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
            if(cleanSurfaceView()) {
                removeOnLayoutChangeListener(layoutChangedListener)
            }

            val exclude = emptyList<Rect>()
            val limit = Rect()
            getLocalVisibleRect(limit)
            touchHelper.setStrokeWidth(pencilWidth)
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

    private var refreshCount = 0
    fun refreshUI(count: Int) {
        if (count != refreshCount) {
            refreshCount = count
            bitmap?.let { bmp->
                holder.lockCanvas()?.let { lockCanvas->
                    lockCanvas.drawColor(Color.WHITE)
                    lockCanvas.drawBitmap(bmp, 0f, 0f, bmpPaint)
                    holder.unlockCanvasAndPost(lockCanvas)
                }

            }
            touchHelper.setRawDrawingEnabled(false)
            touchHelper.setRawDrawingEnabled(true)
        }
    }

    private fun ensureBitmap() :Pair<Bitmap, Canvas> {
        if(bitmap == null) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
            bmpCanvas = Canvas(bitmap!!)
        }
        return Pair(bitmap!!, bmpCanvas!!)
    }

    private fun drawPointsToBitmap(points: List<TouchPoint>) {
        val (targetBmp, canvas) = ensureBitmap()

        val paint = if(isPencil) pathPaint else eraserPaint
        val path = Path()
        val prePoint = PointF(points.get(0).x, points.get(0).y)
        path.moveTo(prePoint.x, prePoint.y)
        for (point in points) {
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }
        canvas.drawPath(path, paint)
        onUpdate(targetBmp)
    }

    fun startPenRender() {
        touchHelper.setRawDrawingEnabled(true)
        touchHelper.isRawDrawingRenderEnabled = true
    }

    private var isPencil = true
    private val isEraser : Boolean
        get() = !isPencil

    private fun pencil() {
        if (isPencil)
            return
        isPencil = true
        touchHelper.setStrokeWidth(pencilWidth)
            .setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
            .setStrokeColor(Color.BLACK)
    }

    private fun eraser() {
        if (isEraser)
            return
        isPencil = false
        touchHelper.setStrokeWidth(eraserWidth)
            .setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
            .setStrokeColor(Color.WHITE)
    }

    fun penOrEraser(isPen: Boolean ) {
        if(isPen == isPencil)
            return
        if (isPen) {
            pencil()
        } else {
            eraser()
        }
    }

    private var pageIdx = 0
    fun onPageIdx(idx: Int, bitmapLoader: (Int)->Bitmap) {
        if(pageIdx == idx)
            return

        pageIdx = idx
        // TODO: load bmp, etc.
    }

    private fun cleanSurfaceView(): Boolean {
        if (holder == null) {
            return false
        }
        val canvas: Canvas = holder.lockCanvas() ?: return false
        val (targetBmp, _) = ensureBitmap()
        canvas.drawColor(Color.WHITE)
        initialBmp?.let {
            canvas.drawBitmap(it,
                Rect(0, 0, it.width, it.height),
                Rect(0, 0, targetBmp.width, targetBmp.height),
                bmpPaint)
            initialBmp = null
        }
        holder.unlockCanvasAndPost(canvas)
        onUpdate(targetBmp)
        return true
    }

    private var onUpdate: (bmp: Bitmap) -> Unit = {}

    fun setOnUpdateListener(updateBmpListener: (bmp: Bitmap) -> Unit) {
        onUpdate = updateBmpListener
    }

}