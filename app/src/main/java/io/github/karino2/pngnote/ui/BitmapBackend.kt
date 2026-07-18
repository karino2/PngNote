package io.github.karino2.pngnote.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.*
import io.github.karino2.pngnote.BookActivity
import java.util.concurrent.Executors
import kotlin.concurrent.withLock
import androidx.core.graphics.createBitmap

/*
   BitmapBackendは背後で持つBitmapとそのCanvasを扱う。
*/
class BitmapBackend {
    var bitmap: Bitmap? = null
        private set
    var bmpCanvas: Canvas? = null
        private set

    private val undoList = UndoList()

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

    fun drawOrErasePathToBitmap(path: Path, paint: Paint, width: Int, height: Int) {
        val (targetBmp, canvas) = ensureBitmap(width, height)
        // undo-redo push and draw.
        val region = pathBound(path, width, height)

        if (region.height() <= 0 || region.width() <= 0)
            return

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

    fun ensureBitmap(width: Int, height: Int): Pair<Bitmap, Canvas> {
        if (bitmap == null) {
            bitmap = createBitmap(width, height).apply {
                eraseColor(Color.WHITE)
            }
            bmpCanvas = Canvas(bitmap!!)
        }
        return Pair(bitmap!!, bmpCanvas!!)
    }

    fun newBitmap(width: Int, height: Int) : Pair<Bitmap, Canvas> {
        bitmap = createBitmap(width, height).apply {
            eraseColor(Color.WHITE)
        }
        bmpCanvas = Canvas(bitmap!!)
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

    private val bmpPaint = Paint(Paint.DITHER_FLAG)

    fun setupNewPage(width: Int, height: Int, newBmp: Bitmap?) {
        val (offscreenBmp, canvas) = ensureBitmap(width, height)
        BookActivity.bitmapLock.withLock {
            offscreenBmp.eraseColor(Color.WHITE)
            newBmp?.let {
                canvas.drawBitmap(
                    it,
                    Rect(0, 0, it.width, it.height),
                    Rect(0, 0, width, height),
                    bmpPaint
                )
            }
        }
        clearUndo()
        notifyUndoStateChanged()
        notifyBitmapUpdate()
    }

    fun resize(width: Int, height: Int, initialBmp: Bitmap?) {
        bitmap?.let { oldbmp ->
            val (_, bcanvas) = newBitmap(width, height)
            BookActivity.bitmapLock.withLock {
                bcanvas.drawBitmap(
                    oldbmp,
                    Rect(0, 0, oldbmp.width, oldbmp.height),
                    Rect(0, 0, width, height),
                    bmpPaint
                )
            }
            notifyBitmapUpdate()
        } ?: return cleanInit(width, height, initialBmp)
    }
}
