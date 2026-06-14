package io.github.karino2.fastfile

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.util.Date
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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

        fun listFiles(resolver: ContentResolver, parent: Uri) : List<FastFile> {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
            val cursor = resolver.query(childrenUri, null,
                null, null, null, null) ?: return emptyList()

            /*
              sequenceを使うと、途中で中断されるとcursor.useの終了処理が呼ばれずに
              A resource failed to call CursorWindow.close.
              というログが出るのでListにする。
             */
            return cursor.use { cur ->
                val result = mutableListOf<FastFile>()
                while(cur.moveToNext()) {
                    val docId = cur.getString(0)
                    val uri = DocumentsContract.buildDocumentUriUsingTree(parent, docId)
                    result.add(fromCursor(cur, uri, resolver))
                }
                result
            }
        }

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

    fun readText()  = resolver.openFileDescriptor(uri, "r")!!.use {desc->
        val fis = FileInputStream(desc.fileDescriptor)
        fis.bufferedReader().use { it.readText() }
    }

    fun writeText(content: String) = resolver.openOutputStream(uri, "wt").use {
        val writer = BufferedWriter(OutputStreamWriter(it))
        writer.use {
            writer.write(content)
        }
    }

    fun readGZIPText() = resolver.openInputStream(uri)!!.use {
        val gzis = GZIPInputStream(it)
        gzis.bufferedReader().use { reader -> reader.readText() }
    }

    fun writeGZIPText(content: String) = resolver.openOutputStream(uri, "wt").use {
        val gzos = GZIPOutputStream(it)
        val writer = BufferedWriter(OutputStreamWriter(gzos))
        writer.use { w ->
            w.write(content)
        }
    }


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

    fun findFileRec(relativePath: String) : FastFile? {
        return relativePath.split("/").fold(this as FastFile?) { acc, disp -> acc?.findFile(disp) }
    }

    fun nameToMimeType(name: String) : String {
        val extension = name.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    fun createFileRec(relativePath: String) : FastFile? {
        val paths =  relativePath.split("/")
        val last = paths.last()
        val parent = paths.dropLast(1).fold(this as FastFile) { acc, disp -> acc.findFile(disp) ?: acc.createDirectory(disp)!! }
        val mimeType = nameToMimeType(last)

        return parent.createFile(mimeType, last)
    }

    fun createDirectory(displayName: String): FastFile? {
        val resUri = DocumentsContract.createDocument(resolver, uri, DocumentsContract.Document.MIME_TYPE_DIR, displayName) ?: return null
        return fromDocUri(resolver, resUri)
    }

    fun ensureDirectory(displayName: String) : FastFile? {
        return findFile(displayName) ?: createDirectory(displayName)
    }

    val isEmpty : Boolean
        get(){
            if (!isFile)
                return false
            return 0L == size
        }

}