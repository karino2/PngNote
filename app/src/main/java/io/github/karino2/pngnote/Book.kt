package io.github.karino2.pngnote

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import java.util.*

// similar to DocumentFile, but store metadata at first query.
data class FastFile(val uri: Uri, val name: String, val lastModified: Long, val mimeType: String, val size: Long, val resolver: ContentResolver) {
    companion object {
        private fun getLong(cur: Cursor, columnName: String) : Long {
            val index = cur.getColumnIndex(columnName)
            if (cur.isNull(index))
                return 0L
            return cur.getLong(index)
        }

        private fun getString(cur: Cursor, columnName: String) : String {
            val index = cur.getColumnIndex(columnName)
            if (cur.isNull(index))
                return ""
            return cur.getString(index)
        }

        private fun fromCursor(
            cur: Cursor,
            uri: Uri,
            resolver: ContentResolver
        ): FastFile {
            val disp = getString(cur, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val lm = getLong(cur, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val mimeType = getString(cur, DocumentsContract.Document.COLUMN_MIME_TYPE)
            val size = getLong(cur, DocumentsContract.Document.COLUMN_SIZE)
            val file = FastFile(uri, disp, lm, mimeType, size, resolver)
            return file
        }

        fun listFiles(resolver: ContentResolver, parent: Uri) : Sequence<FastFile> {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
            val cursor = resolver.query(childrenUri, null,
                null, null, null, null) ?: return emptySequence()

            return sequence {
                cursor.use {cur ->
                    while(cur.moveToNext()) {
                        val docId = cur.getString(0)
                        val uri = DocumentsContract.buildDocumentUriUsingTree(parent, docId)

                        yield(fromCursor(cur, uri, resolver))
                    }
                }
            }
        }

        /*
           @Nullable
    private static String queryForString(Context context, Uri self, String column,
            @Nullable String defaultValue) {
        final ContentResolver resolver = context.getContentResolver();

        Cursor c = null;
        try {
            c = resolver.query(self, new String[]{column}, null, null, null);
            if (c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            } else {
                return defaultValue;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed query: " + e);
            return defaultValue;
        } finally {
            closeQuietly(c);
        }
    }
         */
        // Similar to DocumentFile:fromTreeUri.
        // treeUri is Intent#getData() of ACTION_OPEN_DOCUMENT_TREE
        fun fromTreeUri(context: Context, treeUri: Uri) : FastFile {
            val docId = (if(DocumentsContract.isDocumentUri(context, treeUri)) DocumentsContract.getDocumentId(treeUri) else DocumentsContract.getTreeDocumentId(treeUri))
                ?: throw IllegalArgumentException("Could not get documentUri from $treeUri")
            val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId) ?: throw NullPointerException("Failed to build documentUri from $treeUri")
            val resolver = context.contentResolver
            return fromDocUri(resolver, treeDocUri) ?: throw IllegalArgumentException("Could not query from $treeUri")
        }

        fun fromDocUri(
            resolver: ContentResolver,
            treeDocUri: Uri
        ) : FastFile? {
            val cursor = resolver.query(
                treeDocUri, null,
                null, null, null, null
            ) ?: return null
            cursor.use { cur ->
                if (!cur.moveToFirst())
                    return null

                return fromCursor(cur, treeDocUri, resolver)
            }
        }

    }
    val isDirectory : Boolean
        get() = DocumentsContract.Document.MIME_TYPE_DIR == mimeType

    val isFile: Boolean
        get() = !(isDirectory || mimeType == "")

    //
    //  funcs below are for directory only
    //

    fun createFile(fileMimeType: String, fileDisplayName: String) : FastFile? {
        return DocumentsContract.createDocument(resolver, uri, fileMimeType, fileDisplayName) ?.let {
            //  this last modified might be slight different to real file lastModified, but I think it's not big deal.
            FastFile(it, fileDisplayName, (Date()).time, fileMimeType, 0, resolver)
        }
    }

    fun listFiles() =  listFiles(resolver, uri)

    fun findFile(targetDisplayName: String) = listFiles().find { it.name == targetDisplayName }

    fun createDirectory(displayName: String): FastFile? {
        val resUri = DocumentsContract.createDocument(resolver, uri, DocumentsContract.Document.MIME_TYPE_DIR, displayName) ?: return null
        return fromDocUri(resolver, resUri)
    }
}



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

    fun getPage(idx: Int) = BookPage(pages[idx], idx)

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

class BookIO(private val resolver: ContentResolver) {
    private fun loadBitmap(file: FastFile) : Bitmap {
        return resolver.openFileDescriptor(file.uri, "r").use {
            BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor)
        }
    }

    private fun loadThumbnail(
        bookDir: FastFile,
        displayName: String
    ): Bitmap? {
        return bookDir.findFile(displayName)?.let { loadBitmapThumbnail(it, 3) }
    }

    fun loadThumbnail(bookDir: FastFile) : Bitmap? {
        return loadThumbnail(bookDir, "0000.png")
    }

    fun loadBgThumbnail(bookDir: FastFile) : Bitmap? {
        return loadThumbnail(bookDir, "background.png")
    }


    fun loadPageThumbnail(file: FastFile) = loadBitmapThumbnail(file, 4)
    fun loadBgForGrid(bookDir: FastFile) = bookDir.findFile("background.png")?.let { loadBitmapThumbnail(it, 4) }

    private fun loadBitmapThumbnail(file: FastFile, sampleSize: Int) :Bitmap {
        return resolver.openFileDescriptor(file.uri, "r").use {
            val option = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFileDescriptor(it!!.fileDescriptor, null, option)
        }
    }

    private fun isEmpty(file: FastFile) : Boolean {
        if (!file.isFile)
            return false
        return 0L == file.size
    }

    fun isPageEmpty(page: BookPage) = isEmpty(page.file)
    fun loadBitmap(page: BookPage) = loadBitmap(page.file)

    fun loadBitmapOrNull(page: BookPage) = if(isPageEmpty(page)) null else loadBitmap(page)

    fun loadBgOrNull(book: Book) = book.bgImage?.let { loadBitmap(it) }

    fun saveBitmap(page: BookPage, bitmap: Bitmap) {
        resolver.openOutputStream(page.file.uri, "wt").use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, it!!)
        }
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