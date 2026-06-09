package io.github.karino2.pngnote.ui

import android.content.Context
import android.graphics.*
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList


class CanvasBoox(context: Context, var initialBmp: Bitmap? = null, private val background: Bitmap?, initialPageIdx:Int  = 0) : SurfaceView(context) {
    private val bitmapBackend = BitmapBackend()

    private val pencilWidth = 3f
    private val eraserWidth = 30f

    private val bmpPaint = Paint(Paint.DITHER_FLAG)
    private val bmpPaintWithBG = Paint(Paint.DITHER_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
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
        isAntiAlias = false
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = eraserWidth
    }

    private var undoCount = 0
    private var redoCount = 0

    fun undo(count : Int) {
        if (undoCount != count) {
            undoCount = count
            bitmapBackend.undo()
            refreshAfterUndoRedo()
        }
    }

    fun redo(count: Int) {
        if(redoCount != count) {
            redoCount = count
            bitmapBackend.redo()
            refreshAfterUndoRedo()
        }
    }

    private fun refreshAfterUndoRedo() {
        bitmapBackend.notifyBitmapUpdate()
        bitmapBackend.notifyUndoStateChanged()
        refreshUI()
    }

    private var initCount = 0

    private val inputCallback : RawInputCallback = object: RawInputCallback() {
        override fun onBeginRawDrawing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onRawDrawingTouchPointMoveReceived(p0: TouchPoint?) {
        }

        override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
            drawPointsToBitmap(plist.points)

            // eraser tends to fail for update screen.
            // I don't know the reason. Just update every time eraser coming.
            if (isEraser)
                refreshUI()
        }

        override fun onBeginRawErasing(p0: Boolean, p1: TouchPoint?) {
            // Log.d("PngNote", "erase begin")
            EpdController.enablePost(this@CanvasBoox, 1)
            bitmapBackend.clearEraseAccPoints()
            bitmapBackend.addErasePoint(p1!!)
            updateBmpToSurface()
        }

        override fun onEndRawErasing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onRawErasingTouchPointMoveReceived(p0: TouchPoint?) {
            bitmapBackend.addErasePoint(p0!!)
            if(bitmapBackend.needEraseUpdate) {
                // Log.d("PngNote", "erase update")
                eraseByPointsAndUpdate()
            }
        }

        override fun onRawErasingTouchPointListReceived(plist: TouchPointList) {
            // Log.d("PngNote", "point list, update")
            eraseByPointsAndUpdate()
        }

        private fun eraseByPointsAndUpdate() {
            bitmapBackend.eraseByPoints(width, height, eraserPaint)
            updateBmpToSurface()
        }

    }

    private var lastOpenedLimit: Rect? = null

    private fun calcVisibleRect(): Rect {
        val limit = Rect()
        getLocalVisibleRect(limit)

        // I don't know the reason, but this geometry seems to lower for 40px.
        limit.offset(0, -40)

        return limit
    }

    private fun isRawRenderingBecomesStale(limit: Rect): Boolean =
        touchHelper.isRawDrawingRenderEnabled && (lastOpenedLimit == null || limit.width() != lastOpenedLimit?.width() || limit.height() != lastOpenedLimit?.height())


    private val touchHelper by lazy { TouchHelper.create(this, inputCallback) }

    private fun ensureCloseRawRendering() {
        if (touchHelper.isRawDrawingRenderEnabled) {
            touchHelper.setRawDrawingEnabled(false)
            touchHelper.isRawDrawingRenderEnabled = false
            touchHelper.closeRawDrawing()
        }
    }

    private fun applyRawDrawingSettings() {
        val limit = calcVisibleRect()
        if (!holder.surface.isValid || limit.width() <= 0 || limit.height() <= 0) return

        if (isRawRenderingBecomesStale(limit)) {
            ensureCloseRawRendering()
        }

        if (!touchHelper.isRawDrawingRenderEnabled) {
            clearSurfaceViewByBitmap(holder)
            touchHelper.setStrokeWidth(pencilWidth)
                .setStrokeColor(Color.BLACK)
                .setLimitRect(limit, emptyList<Rect>())
                .setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
                .openRawDrawing()
            touchHelper.setRawDrawingEnabled(true)
            touchHelper.isRawDrawingRenderEnabled = true
            lastOpenedLimit = Rect(limit)
        } else {
            touchHelper.setLimitRect(limit, emptyList<Rect>())
        }
    }

    private val layoutChangedListener : OnLayoutChangeListener by lazy {
        OnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            applyRawDrawingSettings()
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
            applyRawDrawingSettings()
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
        }

    } }

    private fun clearSurfaceViewByBitmap(holder: SurfaceHolder) {
        bitmapBackend.bitmap?.let { bmp ->
            holder.lockCanvas()?.let { lockCanvas ->
                lockCanvas.drawColor(Color.WHITE)
                val paint = background?.let { bg ->
                    lockCanvas.drawBitmap(bg, 0f, 0f, bmpPaint)
                    bmpPaintWithBG
                } ?: bmpPaint
                lockCanvas.drawBitmap(bmp, 0f, 0f, paint)
                holder.unlockCanvasAndPost(lockCanvas)
            }
            true
        } ?: cleanSurfaceView()
    }

    fun firstInit() {
        touchHelper
        addOnLayoutChangeListener(layoutChangedListener)
        holder.addCallback(surfaceCallback)
    }

    fun ensureInit(callCount: Int) {
        if(callCount == 1 && initCount != 1) {
            initCount = 1
            applyRawDrawingSettings()
        }
    }

    private var restartCount = 0

    fun onRestart(count: Int) {
        if (count != restartCount) {
            applyRawDrawingSettings()
            refreshUI()

            restartCount = count
        }
    }

    private var tryRawDrawingCount = 0

    fun onTryRawDrawing(count: Int) {
        if (count != tryRawDrawingCount) {
            applyRawDrawingSettings()
            tryRawDrawingCount = count
        }
    }


    private var ensureCloseCount = 0

    fun onEnsureClose(count: Int) {
        if (count != ensureCloseCount) {
            ensureCloseRawRendering()

            ensureCloseCount = count
        }
    }

    private fun drawPointsToBitmap(points: List<TouchPoint>) {
        val paint = if(isPencil) pathPaint else eraserPaint
        bitmapBackend.drawOrErasePointsToBitmap(points, paint, width, height)
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

        refreshUI()
    }

    private fun eraser() {
        if (isEraser)
            return
        isPencil = false


        touchHelper.setStrokeWidth(eraserWidth)
            .setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
            .setStrokeColor(Color.WHITE)

        refreshUI()
    }


    private var refreshCount = 0
    fun refreshUI(count: Int) {
        if(refreshCount != count) {
            refreshUI()
            refreshCount = count
        }
    }


    private fun refreshUI() {
        updateBmpToSurface()

        touchHelper.setRawDrawingEnabled(false)
        touchHelper.setRawDrawingEnabled(true)
    }

    private fun updateBmpToSurface() {
        val (bmp, _) = bitmapBackend.ensureBitmap(this.width, this.height)
        holder.lockCanvas()?.let { lockCanvas ->
            lockCanvas.drawColor(Color.WHITE)
            val paint = background?.let { bg->
                lockCanvas.drawBitmap(bg, 0f, 0f, bmpPaint)
                bmpPaintWithBG
            } ?: bmpPaint
            lockCanvas.drawBitmap(bmp, 0f, 0f, paint)
            holder.unlockCanvasAndPost(lockCanvas)
        }
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

    private var pageIdx = initialPageIdx
    fun onPageIdx(idx: Int, bitmapLoader: (Int)->Bitmap?) {
        if(pageIdx == idx)
            return

        pageIdx = idx

        val newbmp = bitmapLoader(idx)
        bitmapBackend.setupNewPage(width, height, newbmp)

        refreshUI()
    }

    private fun cleanSurfaceView(): Boolean {
        if (holder == null) {
            return false
        }
        val canvas: Canvas = holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
        initialBmp?.let {
            val paint = background?.let { bg->
                canvas.drawBitmap(bg,
                    Rect(0, 0, bg.width, bg.height),
                    Rect(0, 0, width, height),
                    bmpPaint
                )
                bmpPaintWithBG
            } ?: bmpPaint
            canvas.drawBitmap(it,
                Rect(0, 0, it.width, it.height),
                Rect(0, 0, width, height),
                paint)
            bitmapBackend.cleanInit(width, height, initialBmp)
            initialBmp = null
        }
        holder.unlockCanvasAndPost(canvas)
        return true
    }

    fun setOnUpdateListener(updateBmpListener: (bmp: Bitmap) -> Unit) {
        bitmapBackend.updateBmpListener = updateBmpListener
    }

    fun setOnUndoStateListener(undoStateListener: (undo:Boolean, redo:Boolean) -> Unit) {
        bitmapBackend.undoStateListener = undoStateListener
    }


}