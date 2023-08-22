package io.github.karino2.pngnote

import android.content.Context
import android.graphics.*
import android.util.Size
import android.view.MotionEvent
import android.view.View
import kotlin.concurrent.withLock


class CanvasBoox(context: Context, var initialBmp: Bitmap? = null, private val background: Bitmap?, initialPageIdx:Int  = 0) : View(context) {
    // bitmap committed.
    var bitmap: Bitmap? = null
    private lateinit var bmpCanvas: Canvas

    private val pencilWidth = 3f
    private val eraserWidth = 30f

    private val bmpPaint = Paint(Paint.DITHER_FLAG)
    private val bmpPaintWithBG = Paint(Paint.DITHER_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY) }
    private val pathPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
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
            BookActivity.bitmapLock.withLock {
                undoList.undo(bmpCanvas)
            }

            refreshAfterUndoRedo()
        }
    }

    fun redo(count: Int) {
        if(redoCount != count) {
            redoCount = count
            BookActivity.bitmapLock.withLock {
                undoList.redo(bmpCanvas)
            }

            refreshAfterUndoRedo()
        }
    }

    var isViewMode = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private fun notifyUndoStateChanged() {
        undoStateListener(canUndo, canRedo)
    }

    private fun refreshAfterUndoRedo() {
        updateBmpListener(bitmap!!)
        notifyUndoStateChanged()
        invalidate()
    }

    val canUndo: Boolean
        get() = undoList.canUndo

    val canRedo: Boolean
        get() = undoList.canRedo



    private var initCount = 0

    // use for short term temporary only.
    private val tempRegion = RectF()
    private val tempRect = Rect()
    private fun pathBound(path: Path) : Rect {
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

    var loupePanel: LoupePanel? = null
    private fun ensureLoupePanel(w: Int, h:Int) {
        loupePanel?.let {
            return
        }
        val height = h/3

        loupePanel = LoupePanel(height/5, Size(w, height), this).apply {
            // try place to bottom. Adjust is called inside trySetY.
            trySetY(h)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        ensureLoupePanel(w, h)

        bitmap?.let { oldbmp ->
            createNewCanvas(w, h)
            drawBitmap(oldbmp)
        } ?: setupNewCanvasBitmap(w, h)

        updateBmpListener(bitmap!!)
    }

    private fun setupNewCanvasBitmap(w: Int, h: Int) {
        createNewCanvas(w, h)

        drawBitmap(initialBmp)
        initialBmp = null
    }

    private fun createNewCanvas(w: Int, h: Int) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        bitmap = bmp
        bmpCanvas = Canvas(bmp)
    }

    private fun drawBitmap(srcBitmap: Bitmap?) {
        srcBitmap?.let { src ->
            bmpCanvas.drawBitmap(
                src,
                Rect(0, 0, src.width, src.height),
                Rect(0, 0, this.bitmap!!.width, this.bitmap!!.height),
                bmpPaint
            )
        }
    }

    enum class NavigatorState {
        DORMANT, MOVING
    }

    private var navigatorState = NavigatorState.DORMANT

    // touch pos
    private val navigatorDraggingStartTouch = PointF()
    // navigator origin for staring.
    private val navigatorDraggingStartOrigin = PointF()

    private fun adjustNavigatorOriginX(x: Float) = x.coerceIn(0F, width - loupePanel!!.navigatorRect.width())
    private fun adjustNavigatorOriginY(y: Float) = y.coerceIn(0F, height - loupePanel!!.navigatorRect.height())

    private fun placeNavigator(cx: Float, cy: Float) : Boolean {
        return loupePanel?.let { lp->
            val originX = adjustNavigatorOriginX(cx-lp.navigatorRect.width()/2)
            val originY = adjustNavigatorOriginY(cy-lp.navigatorRect.height()/2)
            navigatorDraggingStartOrigin.set(originX, originY)
            lp.placeNavigator(navigatorDraggingStartOrigin)
            navigatorDraggingStartTouch.set(cx, cy)
            true
        } ?: false
    }


    private val tmpPoint = PointF()

    // 1 is finter, 2 is stylus.
    private val MotionEvent.isStylus : Boolean
        get() = this.getToolType(0) == 2

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(isViewMode)
            return true

        if(navigatorState == NavigatorState.MOVING) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if(placeNavigator(event.x, event.y)) {
                        navigatorState = NavigatorState.MOVING
                        invalidate()
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    moveNavigator(event)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    moveNavigator(event)
                    invalidate()
                    navigatorState = NavigatorState.DORMANT
                    return true
                }
            }
        }


        tmpPoint.set(event.x, event.y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (true == loupePanel?.onTouchDown(tmpPoint, event.isStylus))
                    return true

                if(placeNavigator(event.x, event.y)) {
                    navigatorState = NavigatorState.MOVING
                    invalidate()
                    return true
                }
                // fall through
            }
            MotionEvent.ACTION_MOVE -> {
                if (true == loupePanel?.onTouchMove(tmpPoint))
                    return true
                // fall through
            }
            MotionEvent.ACTION_UP -> {
                if (true == loupePanel?.onTouchUp(tmpPoint))
                    return true
                // fall through
            }
        }
        return super.onTouchEvent(event)
    }

    private fun moveNavigator(event: MotionEvent) {
        val diffX = event.x - navigatorDraggingStartTouch.x
        val diffY = event.y - navigatorDraggingStartTouch.y
        val candX = navigatorDraggingStartOrigin.x + diffX
        val candY = navigatorDraggingStartOrigin.y + diffY
        tmpPoint.set(adjustNavigatorOriginX(candX), adjustNavigatorOriginY(candY))
        loupePanel!!.placeNavigator(tmpPoint)
    }

    private var downHandled = false
    private var prevX = 0f
    private var prevY = 0f
    private val TOUCH_TOLERANCE = 4f

    private val path = Path()



    // onPseudoTouchXXX is called from LoupePanel.
    fun onPseudoTouchDown(x: Float, y: Float) {
        downHandled = true
        path.reset()
        path.moveTo(x, y)
        prevX = x
        prevY = y
        invalidate()
    }

    fun onPseudoTouchMove(x: Float, y: Float) {
        if (downHandled) {
            val dx = Math.abs(x - prevX)
            val dy = Math.abs(y - prevY)
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                path.quadTo(prevX, prevY, (x + prevX) / 2, (y + prevY) / 2)
                prevX = x
                prevY = y
                invalidate()
            }
        }
    }

    fun onPseudoTouchUp(x: Float, y: Float) {
        if (downHandled) {
            downHandled = false
            path.lineTo(x, y)


            val region = pathBound(path)
            val undo = Bitmap.createBitmap(
                bitmap!!,
                region.left,
                region.top,
                region.width(),
                region.height()
            )
            drawPathToCanvas(bmpCanvas, path)
            val redo = Bitmap.createBitmap(
                bitmap!!,
                region.left,
                region.top,
                region.width(),
                region.height()
            )
            undoList.pushUndoCommand(region.left, region.top, undo, redo)

            notifyUndoStateChanged()
            updateBmpListener(bitmap!!)
            path.reset()
            invalidate()
        }
    }


    fun drawPathToCanvas(canvas: Canvas, path: Path) {
        val paint = paintPencilOrEraser()
        canvas.drawPath(path, paint)
    }

    private fun paintPencilOrEraser(): Paint {
        return if (isPencil) pathPaint else eraserPaint
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap!!, 0f, 0f, bmpPaint)
        drawPathToCanvas(canvas, path)

        if (!isViewMode)
            loupePanel?.let { it.draw(canvas, path, paintPencilOrEraser()) }
    }

    private var isPencil = true
    private val isEraser : Boolean
        get() = !isPencil


    private fun pencil() {
        if (isPencil)
            return
        isPencil = true
    }

    private fun eraser() {
        if (isEraser)
            return
        isPencil = false
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

        bitmap!!.eraseColor(Color.WHITE)
        newbmp?.let {
            bmpCanvas.drawBitmap(it,
                Rect(0, 0, it.width, it.height),
                Rect(0, 0, width, height),
                bmpPaint)
        }
        undoList.clear()
        notifyUndoStateChanged()
        invalidate()
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