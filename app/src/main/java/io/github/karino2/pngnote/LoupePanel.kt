package io.github.karino2.pngnote

import android.graphics.*
import android.util.Size
import androidx.core.graphics.toRect
import androidx.core.graphics.toRectF
import java.lang.Integer.min

class LoupePanel(private val toolHeight:Int, private val size: Size, private val holder: CanvasBoox) {
    private var x = 5
    var y = 5
    var scale = 3/2F

    // in holder coordinate.
    private val navigatorPoint = PointF()

    // in holder coordinate
    val navigatorRect = RectF()
    private fun updateNavigatorRect() {
        navigatorRect.set(navigatorPoint.x, navigatorPoint.y, navigatorPoint.x+size.width/scale, navigatorPoint.y+size.height/scale)
    }

    fun placeNavigator(origin : PointF) {
        navigatorPoint.set(origin)
        updateNavigatorRect()
    }

    private val tmpHolderPt = PointF()
    private fun toHolderCoordinate(localPt: PointF) : PointF {
        val relativeX = localPt.x/scale
        val relativeY = localPt.y/scale
        tmpHolderPt.set(navigatorRect.left+relativeX, navigatorRect.top+relativeY)
        return tmpHolderPt
    }

    private val tmpLocalPt = PointF()
    private fun toLocalPoint(gp: PointF) : PointF {
        val relativeX = gp.x - x
        val relativeY = gp.y - (toolHeight+y)
        tmpLocalPt.set(relativeX, relativeY)
        return tmpLocalPt
    }



    enum class State {
        DORMANT,
        MOVING,
        DRAWING
    }

    private var state = State.DORMANT

    private fun isInside(pt: Float, origin: Int, width: Int) =  (pt > origin) && (pt < origin+width+1)

    private fun isInsideHandle(gp: PointF) : Boolean {
        return isInside(gp.x, x, toolHeight) && isInside(gp.y, y, toolHeight)
    }

    private fun isInsidePaintArea(gp: PointF) : Boolean {
        return isInside(gp.x, x, size.width) && isInside(gp.y, y+toolHeight, size.height)
    }

    private var moveOrigin = PointF()
    private var moveOriginY = y

    // gp: point based on holder coordinate.
    // return true if handled.
    fun onTouchDown(gp: PointF) : Boolean {
        when(state) {
            // if down come again while handling previous drag.
            // send up to finish prev dragging.
            State.DRAWING -> {
                sendTouchUp(gp)
            }
            State.MOVING, State.DORMANT -> {}
        }

        if (isInsideHandle(gp)) {
            state = State.MOVING
            moveOrigin.set(gp)
            moveOriginY = y
            return true
        }
        if (isInsidePaintArea(gp)) {
            state = State.DRAWING
            updateNavigatorRect()
            val localPt = toLocalPoint(gp)
            val holderPt = toHolderCoordinate(localPt)

            holder.onPseudoTouchDown(holderPt.x, holderPt.y)
            return true
        }
        return false
    }

    private fun adjustYInsideView() {
        y = min(holder.height - (toolHeight+size.height), y)
    }

    fun trySetY(yCandidate : Int) {
        y = yCandidate
        adjustYInsideView()
    }

    fun onTouchMove(gp: PointF) : Boolean {
        return when(state) {
            State.DORMANT-> false
            State.MOVING -> {
                updatePanelPos(gp)
                holder.invalidate()
                true
            }
            State.DRAWING -> {
                if (isInsidePaintArea(gp)) {
                    updateNavigatorRect()
                    val localPt = toLocalPoint(gp)
                    val holderPt = toHolderCoordinate(localPt)

                    holder.onPseudoTouchMove(holderPt.x, holderPt.y)
                }
                true
            }
        }
    }

    private fun updatePanelPos(gp: PointF) {
        val diff = gp.y - moveOrigin.y
        y = moveOriginY + diff.toInt()
        adjustYInsideView()
    }

    fun onTouchUp(gp: PointF) : Boolean {
        return when(state) {
            State.DORMANT -> false
            State.MOVING -> {
                updatePanelPos(gp)
                holder.invalidate()
                state = State.DORMANT
                true
            }
            State.DRAWING -> {
                sendTouchUp(gp)
                state = State.DORMANT
                true
            }
        }
    }

    private fun sendTouchUp(gp: PointF) {
        val localPt = toLocalPoint(gp)
        localPt.set( localPt.x.coerceIn(0F, size.width.toFloat()), localPt.y.coerceIn(0F, size.height.toFloat()))
        val holderPt = toHolderCoordinate(localPt)

        holder.onPseudoTouchUp(
            holderPt.x,
            holderPt.y
        )
    }

    private val backgroundFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.LTGRAY
    }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2F
        color = Color.BLACK
    }

    private val navigatorPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2F
        color = Color.MAGENTA
    }

    val tmpRect = Rect()
    fun draw(canvas: Canvas) {
        updateNavigatorRect()
        canvas.drawRect(navigatorRect, navigatorPaint)

        // draw toolbar (handle)
        tmpRect.set(x, y, x+toolHeight, y+toolHeight)
        canvas.drawRect(tmpRect, backgroundFillPaint)
        canvas.drawRect(tmpRect, borderPaint)


        // set paintArea rect
        tmpRect.set(x, y+toolHeight, x+size.width, y+toolHeight+size.height)
        canvas.drawRect(tmpRect, backgroundFillPaint)

        holder.draftBitmap?.let {holderBmp ->
            canvas.drawBitmap(holderBmp, navigatorRect.toRect(), tmpRect, null)
        }

        canvas.drawRect(tmpRect, borderPaint)
    }
}