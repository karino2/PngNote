package io.github.karino2.pngnote

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.widget.Toast
import androidx.core.graphics.createBitmap
import io.github.karino2.fastfile.FastFile


class BookList(val dir: FastFile, val resolver: ContentResolver) {
    companion object {
        private const val LAST_ROOT_DIR_KEY = "last_root_url"

        fun lastUriStr(ctx: Context) = sharedPreferences(ctx).getString(LAST_ROOT_DIR_KEY, null)
        fun writeLastUriStr(ctx: Context, path : String) = sharedPreferences(ctx).edit()
            .putString(LAST_ROOT_DIR_KEY, path)
            .commit()

        fun resetLastUriStr(ctx: Context) = sharedPreferences(ctx).edit()
            .putString(LAST_ROOT_DIR_KEY, null)
            .commit()

        private fun sharedPreferences(ctx: Context) = ctx.getSharedPreferences("KAKIOKU", Context.MODE_PRIVATE)

        fun showMessage(ctx: Context, msg : String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}


class Book(val bookDir: FastFile, val pages: List<FastFile>, val bgImage: FastFile?) {
    fun addPage() : Book {
        // page name start form 0!
        val pngFile = BookPage.createEmptyFile(bookDir, pages.size)
        return Book(bookDir, pages + pngFile, bgImage)
    }

    val pageNum: Int
        get() = pages.size

    fun getPage(idx: Int) = BookPage(pages[idx], idx)

    // Assign dummy size so that file.isEmpty becomes false.
    // We don't care actual size, just check whether it's empty or not.
    // So assign non-zero value is enough for our purpose.
    fun assignNonEmpty(pageIdx: Int): Book {
        if(!getPage(pageIdx).file.isEmpty)
            return this

        return  pages.mapIndexed { idx, file ->
            if(idx != pageIdx)
                file
            else
                file.copy(size=1000)
        }.toList().let { Book(bookDir, it, bgImage) }
    }

    val name : String
        get() = bookDir.name
}


data class BookPage(val file: FastFile, val idx: Int) {
    companion object {
        private fun newPageName(pageIdx: Int) : String {
            return "%04d.png".format(pageIdx)
        }

        fun createEmptyFile(bookDir: FastFile, idx: Int) : FastFile {
            val fileName = newPageName(idx)
            return bookDir.createFile("image/png", fileName) ?: throw Exception("Can't create file $fileName")
        }
    }
}

class BitmapIO(private val resolver: ContentResolver) {
    fun loadBitmap(file: FastFile) : Bitmap {
        return resolver.openFileDescriptor(file.uri, "r").use {
            BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor)
        }
    }

    fun loadBitmapThumbnail(file: FastFile, sampleSize: Int) :Bitmap {
        return resolver.openFileDescriptor(file.uri, "r").use {
            val option = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor, null, option)
        }
    }

    fun saveBitmap(file: FastFile, bitmap: Bitmap) {
        resolver.openOutputStream(file.uri, "wt").use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, it!!)
        }
    }
}

class BookPageIO(private val bitmapIO: BitmapIO) {
    fun isPageEmpty(page: BookPage) = page.file.isEmpty
    fun loadBitmap(page: BookPage) = bitmapIO.loadBitmap(page.file)
    fun loadBitmapOrNull(page: BookPage) = if(isPageEmpty(page)) null else loadBitmap(page)
    fun saveBitmap(page: BookPage, bitmap: Bitmap) {
        bitmapIO.saveBitmap(page.file, bitmap)
    }

    fun loadPageThumbnail(file: FastFile) = bitmapIO.loadBitmapThumbnail(file, 4)
}

class BetweenPageIO(private val pageIO: BookPageIO) {
    private val borderPaint = Paint().apply { color = Color.LTGRAY }
    private val invalidAreaPaint = Paint().apply { color = Color.GRAY }

    fun loadBitmapBetween(book: Book, upperPageIdx: Int): Bitmap? {
        val firstPage = book.getPage(upperPageIdx).let { pageIO.loadBitmapOrNull(it) } ?: return null

        val width = firstPage.width
        val height = firstPage.height
        val res = createBitmap(width, height, firstPage.config)
        val canvas = Canvas(res)

        val halfHeight = height / 2

        drawFirstHalfPage(canvas, firstPage, halfHeight)
        drawCenterBorder(canvas, halfHeight, width)
        drawBottomHalf(upperPageIdx+1, book, canvas, halfHeight, width, height)
        return res
    }

    fun saveBitmapBetween(book: Book, upperPageIdx: Int, bitmap: Bitmap) {
        val firstPage = book.getPage(upperPageIdx)
        val firstPageBmp = pageIO.loadBitmap(firstPage).copy(Bitmap.Config.ARGB_8888, true)
        val width = firstPageBmp.width
        val height = firstPageBmp.height
        val halfHeight = height / 2

        saveFirstPageLowerHalf(firstPage, firstPageBmp, halfHeight, width, height, bitmap)

        if (upperPageIdx + 1 < book.pageNum) {
            saveSecondPageUpperHalf(book, upperPageIdx + 1, halfHeight, width, height, bitmap)
        }
    }

    private fun drawBottomHalf(
        bottomPageIdx: Int,
        book: Book,
        canvas: Canvas,
        halfHeight: Int,
        width: Int,
        height: Int
    ) {
        if (bottomPageIdx == book.pageNum) {
            canvas.drawRect(0f, (halfHeight + 2).toFloat(), width.toFloat(), height.toFloat(), invalidAreaPaint)
        } else {
            val secondPage = book.getPage(bottomPageIdx).let { pageIO.loadBitmapOrNull(it) }
            secondPage?.let { secBmp ->
                val srcLower = Rect(0, 0, width, halfHeight - 2)
                val dstLower = Rect(0, halfHeight + 2, width, height)
                canvas.drawBitmap(secBmp, srcLower, dstLower, null)
            } ?: run {
                canvas.drawRect(0f, (halfHeight + 2).toFloat(), width.toFloat(), height.toFloat(), invalidAreaPaint)
            }
        }
    }

    private fun drawCenterBorder(canvas: Canvas, halfHeight: Int, width: Int) {
        canvas.drawRect(0f, (halfHeight - 2).toFloat(), width.toFloat(), (halfHeight + 2).toFloat(), borderPaint)
    }

    private fun drawFirstHalfPage(canvas: Canvas, firstPage: Bitmap, halfHeight: Int) {
        val width = firstPage.width
        val height = firstPage.height
        val srcUpper = Rect(0, halfHeight + 2, width, height)
        val dstUpper = Rect(0, 0, width, halfHeight - 2)
        canvas.drawBitmap(firstPage, srcUpper, dstUpper, null)
    }

    private fun saveFirstPageLowerHalf(
        firstPage: BookPage,
        firstPageBmp: Bitmap,
        halfHeight: Int,
        width: Int,
        height: Int,
        bitmap: Bitmap
    ) {
        val canvasFirst = Canvas(firstPageBmp)
        val srcUpper = Rect(0, 0, width, halfHeight - 2)
        val firstDst = Rect(0, halfHeight + 2, width, height)
        canvasFirst.drawBitmap(bitmap, srcUpper, firstDst, null)
        pageIO.saveBitmap(firstPage, firstPageBmp)
    }

    private fun saveSecondPageUpperHalf(
        book: Book,
        secPageIdx: Int,
        halfHeight: Int,
        width: Int,
        height: Int,
        bitmap: Bitmap
    ) {
        val secondPage = book.getPage(secPageIdx)
        val secondPageBmp = pageIO.loadBitmap(secondPage).copy(Bitmap.Config.ARGB_8888, true)
        val canvasSecond = Canvas(secondPageBmp)
        val srcLower = Rect(0, halfHeight + 2, width, height)
        val secondDst = Rect(0, 0, width, halfHeight - 2)
        canvasSecond.drawBitmap(bitmap, srcLower, secondDst, null)
        pageIO.saveBitmap(secondPage, secondPageBmp)
    }
}

class BookIO(resolver: ContentResolver) {
    val bitmapIO = BitmapIO(resolver)
    val pageIO = BookPageIO(bitmapIO)

    val betweenPageIO = BetweenPageIO(pageIO)

    private fun loadThumbnail(
        bookDir: FastFile,
        displayName: String,
        sampleSize: Int
    ): Bitmap? {
        return bookDir.findFile(displayName)?.let { bitmapIO.loadBitmapThumbnail(it, sampleSize) }
    }

    fun loadThumbnail(bookDir: FastFile) : Bitmap? {
        return loadThumbnail(bookDir, "0000.png", 3)
    }

    fun loadBgThumbnail(bookDir: FastFile) : Bitmap? {
        return loadThumbnail(bookDir, "background.png", 3)
    }

    fun loadBgForGrid(book: Book) = loadThumbnail(book.bookDir, "background.png", 4)

    fun loadBgOrNull(book: Book) = book.bgImage?.let { bitmapIO.loadBitmap(it) }

    fun loadBitmapOrNull(book: Book, pageIdx: Int, shiftHalf: Boolean) : Bitmap? {
        if(!shiftHalf) return book.getPage(pageIdx).let { pageIO.loadBitmapOrNull(it) }
        return betweenPageIO.loadBitmapBetween(book, pageIdx)
    }

    fun saveBitmap(book: Book, pageIdx: Int, shiftHalf:Boolean, bitmap: Bitmap) {
        if (!shiftHalf) {
            pageIO.saveBitmap(book.getPage(pageIdx), bitmap)
            return
        }
        betweenPageIO.saveBitmapBetween(book, pageIdx, bitmap)
    }


    // ex. 0009.png
    private val pageNamePat = "([0-9][0-9][0-9][0-9])\\.png".toRegex()

    fun loadBook(bookDir: FastFile) : Book {
        val pageMap = bookDir.listFiles()
            .filter {file ->
                pageNamePat.matches(file.name)
            }.map {file ->
                val res = pageNamePat.find(file.name)!!
                val pageIdx = res.groupValues[1].toInt()
                Pair(pageIdx, file)
            }.toMap()
        val lastPageIdx = if(pageMap.isEmpty()) 0 else pageMap.maxOf { it.key }
        val pages = (0 .. lastPageIdx).map {
            pageMap[it] ?: BookPage.createEmptyFile(bookDir, it)
        }
        val bgFile = bookDir.findFile("background.png")
        return Book(bookDir, pages, bgFile)
    }
}
