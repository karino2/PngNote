package io.github.karino2.pngnote.ui

import android.graphics.Bitmap
import android.graphics.Canvas


class UndoList {
    companion object {
        private const val COMMAND_MAX_SIZE = 1024*1024 // 1MB
    }

    internal class UndoCommand(
        private val x: Int,
        private val y: Int,
        private val undoBmp: Bitmap,
        private val redoBmp: Bitmap
    ) {
        fun undo(target: Canvas) {
            target.drawBitmap(undoBmp, x.toFloat(), y.toFloat(), null)
        }

        fun redo(target: Canvas) {
            target.drawBitmap(redoBmp, x.toFloat(), y.toFloat(), null)
        }

        private fun getBitmapSize(bmp: Bitmap): Int {
            return 4 * bmp.width * bmp.height
        }

        val size: Int
            get() = getBitmapSize(undoBmp) + getBitmapSize(redoBmp)

    }

    private val commandList = mutableListOf<UndoCommand>()
    private var currentPos = -1

    fun pushUndoCommand(
        x: Int,
        y: Int,
        undo: Bitmap,
        redo: Bitmap) {
        discardLaterCommand()
        commandList.add(UndoCommand(x, y, undo, redo))
        currentPos++
        discardUntilSizeFit()
    }

    private fun discardLaterCommand() {
        for (i in commandList.size - 1 downTo currentPos + 1) {
            commandList.removeAt(i)
        }
    }

    private fun discardUntilSizeFit() {
        // currentPos ==0, then do not remove even though it bigger than threshold (I guess never happen, though).
        while (currentPos > 0 && commandsSize > COMMAND_MAX_SIZE) {
            commandList.removeAt(0)
            currentPos--
        }
    }

    private val commandsSize: Int
        get(){
            var res = 0
            for (cmd in commandList) {
                res += cmd.size
            }
            return res
        }

    val canUndo : Boolean
        get() = currentPos >= 0

    val canRedo: Boolean
        get() = currentPos < commandList.size - 1

    fun redo(target: Canvas) {
        if (!canRedo) return
        currentPos++
        commandList[currentPos].redo(target)
    }

    fun undo(target: Canvas) {
        if (!canUndo) return
        commandList[currentPos].undo(target)
        currentPos--
    }

    fun clear() {
        commandList.clear()
        currentPos = -1
    }

}