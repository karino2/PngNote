package io.github.karino2.pngnote.ui

import android.graphics.*
import com.onyx.android.sdk.data.note.TouchPoint
import io.github.karino2.pngnote.BookActivity
import java.util.Date
import kotlin.concurrent.withLock
import kotlin.math.abs

/*
    BitmapBackendは背後で持つBitmapとそのCanvasを扱う。
 */
class BitmapBackend {
    private val undoList = UndoList()

    val eraseAccPoints = ArrayList<TouchPoint>()
    private var lastErase = 0L

    private fun getCurrentMills() = (Date()).time

    fun addErasePoint(p: TouchPoint) {
        eraseAccPoints.add(p)
    }

    fun clearEraseAccPoints() {
        lastErase = getCurrentMills()
        eraseAccPoints.clear()
    }

    val needEraseUpdate: Boolean
        get() = eraseAccPoints.size >= 100 || (getCurrentMills() - lastErase) > 300L

    var bitmap: Bitmap? = null
        private set
    var bmpCanvas: Canvas? = null
        private set

    var updateBmpListener: (bmp: Bitmap) -> Unit = {}
    var undoStateListener: (undo: Boolean, redo: Boolean) -> Unit = { _, _ -> }

    fun notifyBitmapUpdate() {
        bitmap?.let { updateBmpListener(it) }
    }

    fun notifyUndoStateChanged() {
        undoStateListener(canUndo, canRedo)
    }

    val canUndo: Boolean
        get() = undoList.canUndo

    val canRedo: Boolean
        get() = undoList.canRedo

    fun clearUndo() {
        undoList.clear()
    }

    fun pushUndoCommand(x: Int, y: Int, undoBmp: Bitmap, redoBmp: Bitmap) {
        undoList.pushUndoCommand(x, y, undoBmp, redoBmp)
    }

    fun undo() {
        bmpCanvas?.let { canvas ->
            BookActivity.bitmapLock.withLock {
                undoList.undo(canvas)
            }
        }
    }

    fun redo() {
        bmpCanvas?.let { canvas ->
            BookActivity.bitmapLock.withLock {
                undoList.redo(canvas)
            }
        }
    }

    private val tempRegion = RectF()
    private val tempRect = Rect()
    private fun pathBound(path: Path, width: Int, height: Int): Rect {
        path.computeBounds(tempRegion, false)
        tempRegion.roundOut(tempRect)
        widen(tempRect, 5, width, height)
        return tempRect
    }

    private fun widen(tmpInval: Rect, margin: Int, width: Int, height: Int) {
        val newLeft = (tmpInval.left - margin).coerceAtLeast(0)
        val newTop = (tmpInval.top - margin).coerceAtLeast(0)
        val newRight = (tmpInval.right + margin).coerceAtMost(width)
        val newBottom = (tmpInval.bottom + margin).coerceAtMost(height)
        tmpInval.set(newLeft, newTop, newRight, newBottom)
    }

    fun drawOrErasePointsToBitmap(
        points: List<TouchPoint>,
        paint: Paint,
        width: Int,
        height: Int
    ) {
        val (targetBmp, canvas) = ensureBitmap(width, height)

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
        val region = pathBound(path, width, height)
        val (undo, redo) = BookActivity.bitmapLock.withLock {
            val undo = Bitmap.createBitmap(
                targetBmp,
                region.left,
                region.top,
                region.width(),
                region.height()
            )
            canvas.drawPath(path, paint)
            val redo = Bitmap.createBitmap(
                targetBmp,
                region.left,
                region.top,
                region.width(),
                region.height()
            )
            Pair(undo, redo)
        }
        pushUndoCommand(region.left, region.top, undo, redo)

        notifyBitmapUpdate()
        notifyUndoStateChanged()
    }
    
    fun eraseByPoints(width: Int, height: Int, eraserPaint: Paint) {
        drawOrErasePointsToBitmap(eraseAccPoints, eraserPaint, width, height)
        clearEraseAccPoints()
        
    }

    fun ensureBitmap(width: Int, height: Int): Pair<Bitmap, Canvas> {
        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
            bmpCanvas = Canvas(bitmap!!)
        }
        return Pair(bitmap!!, bmpCanvas!!)
    }

    fun cleanInit(width: Int, height: Int, initialBmp: Bitmap?) {
        val (targetBmp, canvas) = ensureBitmap(width, height)
        initialBmp?.let {
            BookActivity.bitmapLock.withLock {
                canvas.drawBitmap(
                    it,
                    Rect(0, 0, it.width, it.height),
                    Rect(0, 0, targetBmp.width, targetBmp.height),
                    Paint(Paint.DITHER_FLAG)
                )
            }
        }
        notifyBitmapUpdate()
    }

    fun setupNewPage(width: Int, height: Int, newBmp: Bitmap?) {
        val (offscreenBmp, canvas) = ensureBitmap(width, height)
        BookActivity.bitmapLock.withLock {
            offscreenBmp.eraseColor(Color.WHITE)
            newBmp?.let {
                canvas.drawBitmap(
                    it,
                    Rect(0, 0, it.width, it.height),
                    Rect(0, 0, width, height),
                    Paint(Paint.DITHER_FLAG)
                )
            }
        }
        clearUndo()
        notifyUndoStateChanged()
        notifyBitmapUpdate()
    }
}
