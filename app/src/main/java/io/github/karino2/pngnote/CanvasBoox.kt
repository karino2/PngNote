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
import java.util.*
import kotlin.math.abs


class CanvasBoox(context: Context, var initialBmp: Bitmap? = null) : SurfaceView(context) {
    var bitmap: Bitmap? = null
    private var bmpCanvas: Canvas? = null

    private val pencilWidth = 3f
    private val eraserWidth = 30f

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
        isAntiAlias = false
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = eraserWidth
    }

    private val undoList = UndoList()

    private var undoCount = 0
    private var redoCount = 0

    fun undo(count : Int) {
        if (undoCount != count) {
            undoCount = count
            undoList.undo(bmpCanvas!!)

            refreshAfterUndoRedo()
        }
    }

    fun redo(count: Int) {
        if(redoCount != count) {
            redoCount = count
            undoList.redo(bmpCanvas!!)

            refreshAfterUndoRedo()
        }
    }

    private fun notifyUndoStateChanged() {
        undoStateListener(canUndo, canRedo)
    }

    private fun refreshAfterUndoRedo() {
        updateBmpListener(bitmap!!)
        notifyUndoStateChanged()
        refreshUI()
    }

    val canUndo: Boolean
        get() = undoList.canUndo

    val canRedo: Boolean
        get() = undoList.canRedo



    private var initCount = 0

    private var mightNeedRefresh = false

    private val inputCallback = object: RawInputCallback() {
        override fun onBeginRawDrawing(p0: Boolean, p1: TouchPoint?) {
            // next pen rendering coming to soon before last refresh,
            // UI update might not yet finish.
            // reschedule next refresh for sure.
            if(lastRefresh != -1L && getCurrentMills()-lastRefresh < 500){
                mightNeedRefresh = true
            }
        }

        override fun onEndRawDrawing(p0: Boolean, p1: TouchPoint?) {
        }

        override fun onRawDrawingTouchPointMoveReceived(p0: TouchPoint?) {
        }

        override fun onRawDrawingTouchPointListReceived(plist: TouchPointList) {
            drawPointsToBitmap(plist.points)

            if(mightNeedRefresh) {
                mightNeedRefresh = false
                lastRefresh = getCurrentMills()
                refreshUI()
                return
            }

            // eraser tends to fail for update screen.
            // I don't know the reason. Just update every time eraser coming.
            if (isEraser)
                refreshUI()
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
                .setStrokeColor(Color.BLACK)
                .setLimitRect(limit, exclude)
                .openRawDrawing()
            touchHelper.setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)
        }
    }

    // Initial creation is special, regards initial state as attached.
    private var detached = false

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
            touchHelper.closeRawDrawing()
            detached = true
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
            startRawRendering()
            detached = false
        }
    }

    private var restartCount = 0

    fun onRestart(count: Int) {
        if (count != restartCount) {
            holder.addCallback(surfaceCallback)
            touchHelper.openRawDrawing()
            startRawRendering()
            refreshUI()

            detached = false
            restartCount = count
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

    // use for short term temporary only.
    private val tempRegion = RectF()
    private val tempRect = Rect()
    fun pathBound(path: Path) : Rect {
        path.computeBounds(tempRegion, false)
        tempRegion.roundOut(tempRect)
        widen(tempRect, 5)
        return tempRect
    }

    private fun widen(tmpInval: Rect, margin: Int) {
        val newLeft = (tmpInval.left - margin).coerceAtLeast(0)
        val newTop = (tmpInval.top - margin).coerceAtLeast(0)
        val newRight = (tmpInval.right + margin).coerceAtMost(width)
        val newBottom = (tmpInval.bottom + margin).coerceAtMost(height)
        tmpInval.set(newLeft, newTop, newRight, newBottom)
    }

    private fun drawPointsToBitmap(points: List<TouchPoint>) {
        val (targetBmp, canvas) = ensureBitmap()

        val paint = if(isPencil) pathPaint else eraserPaint
        val path = Path()
        val prePoint = PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)
        for (point in points) {
            // skip strange jump point.
            if (abs(prePoint.y - point.y) >= 30)
                continue
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }

        // undo-redo push and draw.
        val region = pathBound(path)
        val undo = Bitmap.createBitmap(targetBmp, region.left, region.top, region.width(), region.height())
        canvas.drawPath(path, paint)
        val redo = Bitmap.createBitmap(targetBmp, region.left, region.top, region.width(), region.height())
        undoList.pushUndoCommand(region.left, region.top, undo, redo)

        updateBmpListener(targetBmp)
        notifyUndoStateChanged()
    }

    private fun drawBitmapToSurface() {
        val canvas: Canvas = holder.lockCanvas() ?: return
        val (targetBmp, _) = ensureBitmap()
        canvas.drawBitmap(targetBmp,
            Rect(0, 0, targetBmp.width, targetBmp.height),
            Rect(0, 0, width, height),
            bmpPaint
        )
        holder.unlockCanvasAndPost(canvas)
    }

    private fun startRawRendering() {
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

    private fun getCurrentMills() = (Date()).time
    private var lastRefresh = -1L

    private var refreshCount = 0
    fun refreshUI(count: Int) {
        if(refreshCount != count) {
            refreshUI()
            lastRefresh = getCurrentMills()
            refreshCount = count
        }
    }

    private fun refreshUI() {
        val (bmp, _) = ensureBitmap()
        holder.lockCanvas()?.let { lockCanvas->
            lockCanvas.drawColor(Color.WHITE)
            lockCanvas.drawBitmap(bmp, 0f, 0f, bmpPaint)
            holder.unlockCanvasAndPost(lockCanvas)
        }

        touchHelper.setRawDrawingEnabled(false)
        touchHelper.setRawDrawingEnabled(true)
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
    fun onPageIdx(idx: Int, bitmapLoader: (Int)->Bitmap?) {
        if(pageIdx == idx)
            return

        pageIdx = idx

        val newbmp = bitmapLoader(idx)
        val (offscreenBmp, canvas) = ensureBitmap()
        offscreenBmp.eraseColor(Color.WHITE)
        newbmp?.let {
            canvas.drawBitmap(it,
                Rect(0, 0, it.width, it.height),
                Rect(0, 0, width, height),
                bmpPaint)
        }
        undoList.clear()
        notifyUndoStateChanged()

        refreshUI()
    }

    // call multiple time for this cause current canvas to clear.
    // It is not what we want, but it's difficult to clear properly.
    private fun cleanSurfaceView(): Boolean {
        if (holder == null) {
            return false
        }
        val canvas: Canvas = holder.lockCanvas() ?: return false
        val (targetBmp, bmpCanvas) = ensureBitmap()
        canvas.drawColor(Color.WHITE)
        initialBmp?.let {
            canvas.drawBitmap(it,
                Rect(0, 0, it.width, it.height),
                Rect(0, 0, width, height),
                bmpPaint)
            bmpCanvas.drawBitmap(it,
                Rect(0, 0, it.width, it.height),
                Rect(0, 0, targetBmp.width, targetBmp.height),
                bmpPaint)
            initialBmp = null
        }
        holder.unlockCanvasAndPost(canvas)
        updateBmpListener(targetBmp)
        return true
    }

    private var updateBmpListener: (bmp: Bitmap) -> Unit = {}

    fun setOnUpdateListener(updateBmpListener: (bmp: Bitmap) -> Unit) {
        this.updateBmpListener = updateBmpListener
    }

    private var undoStateListener: (undo:Boolean, redo:Boolean) -> Unit = { _, _ ->}
    fun setOnUndoStateListener(undoStateListener: (undo:Boolean, redo:Boolean) -> Unit) {
        this.undoStateListener = undoStateListener
    }


}