package io.github.karino2.pngnote.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View


class CanvasBoox(context: Context, var initialBmp: Bitmap? = null, private val background: Bitmap?, initialPageIdx:Int  = 0) : View(context) {
    private val bitmapBackend = BitmapBackend()

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
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        bitmapBackend.resize(w, h, initialBmp)
        initialBmp = null
    }

    private var downHandled = false
    private var prevX = 0f
    private var prevY = 0f
    private val TOUCH_TOLERANCE = 4f

    private val path = Path()


    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downHandled = true
                path.reset()
                path.moveTo(x, y)
                prevX = x
                prevY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (downHandled) {
                    val dx = Math.abs(x - prevX)
                    val dy = Math.abs(y - prevY)
                    if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                        path.quadTo(prevX, prevY, (x + prevX) / 2, (y + prevY) / 2)
                        prevX = x
                        prevY = y
                        invalidate()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (downHandled) {
                    downHandled = false
                    path.lineTo(x, y)

                    bitmapBackend.drawOrErasePathToBitmap(path, currentPaint(), width, height)

                    path.reset()
                    invalidate()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun currentPaint(): Paint {
        val paint = if (isPencil) pathPaint else eraserPaint
        return paint
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmapBackend.bitmap!!, 0f, 0f, bmpPaint)
        canvas.drawPath(path, currentPaint())
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
        bitmapBackend.setupNewPage(width, height, newbmp)

        invalidate()
    }

    fun setOnUpdateListener(updateBmpListener: (bmp: Bitmap) -> Unit) {
        bitmapBackend.updateBmpListener = updateBmpListener
    }

    fun setOnUndoStateListener(undoStateListener: (undo:Boolean, redo:Boolean) -> Unit) {
        bitmapBackend.undoStateListener = undoStateListener
    }


    /* BOOX のrawrenderingと同じコードにするためのダミー実装 */
    fun firstInit() {}
    fun ensureInit(s:Int) {}
    fun onRestart(count: Int) {}
    fun onTryRawDrawing(count: Int) {}
    fun onEnsureClose(count: Int) {}
    fun refreshUI(count: Int) {}
}