package io.github.karino2.pngnote

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock

class BookList(val dir: DocumentFile, val resolver: ContentResolver) {
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


class Book(val bookDir: DocumentFile, val pages: List<DocumentFile>) {
    fun addPage() : Book {
        // page name start form 0!
        val pngFile = BookPage.createEmptyFile(bookDir, pages.size)
        return Book(bookDir, pages + pngFile)
    }

    fun getPage(idx: Int) = BookPage(pages[idx], idx)
}


data class BookPage(val file: DocumentFile, val idx: Int) {
    companion object {
        private fun newPageName(pageIdx: Int) : String {
            return "%04d.png".format(pageIdx)
        }

        fun createEmptyFile(bookDir: DocumentFile, idx: Int) : DocumentFile {
            val fileName = newPageName(idx)
            return bookDir.createFile("image/png", fileName) ?: throw Exception("Can't create file $fileName")
        }
    }
}

class BookIO(private val resolver: ContentResolver) {
    private fun loadBitmap(file: DocumentFile) : Bitmap {
        return resolver.openFileDescriptor(file.uri, "r").use {
            BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor)
        }
    }

    private fun isEmpty(file: DocumentFile) : Boolean {
        if (!file.isFile)
            return false
        return 0L == file.length()
    }

    fun isPageEmpty(page: BookPage) = isEmpty(page.file)
    fun loadBitmap(page: BookPage) = loadBitmap(page.file)

    fun loadBitmapOrNull(page: BookPage) = if(isPageEmpty(page)) null else loadBitmap(page)

    fun saveBitmap(page: BookPage, bitmap: Bitmap) {
        resolver.openOutputStream(page.file.uri, "wt").use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, it)
        }
    }

    // ex. 0009.png
    private val pageNamePat = "([0-9][0-9][0-9][0-9])\\.png".toRegex()

    fun loadBook(bookDir: DocumentFile) : Book {
        val pageMap = bookDir.listFiles()
            .filter {file ->
                file.name?.let {
                    pageNamePat.matches(it)
                } ?: false
            }.map {file ->
                val res = pageNamePat.find(file.name!!)!!
                val pageIdx = res.groupValues[1].toInt()
                Pair(pageIdx, file)
            }.toMap()
        val lastPageIdx = if(pageMap.isEmpty()) 0 else pageMap.maxOf { it.key }
        val pages = (0 .. lastPageIdx).map {
            pageMap[it] ?: BookPage.createEmptyFile(bookDir, it)
        }
        return Book(bookDir, pages)
    }


}