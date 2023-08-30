package io.github.karino2.pngnote

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import io.github.karino2.pngnote.ui.CanvasBoox
import io.github.karino2.pngnote.ui.theme.PngNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import kotlin.concurrent.withLock


class BookActivity : ComponentActivity() {
    companion object {
        val bitmapLock = java.util.concurrent.locks.ReentrantLock()
    }
    private lateinit var dirUrl : Uri
    private var initialPageIdx = 0

    private val bookDir by lazy {
        FastFile.fromTreeUri(this, dirUrl)
    }

    private val bookIO by lazy { BookIO(contentResolver) }

    private var _book : Book? = null
    set(newbook) {
        field = newbook
        pageNum.value = newbook?.pages?.size ?: 0
    }

    private val book : Book
    get() {
        _book?.let { return it }
        bookIO.loadBook(bookDir).also {
            _book = it
            return it
        }
    }

    private fun showMessage(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()


    private val initCount = MutableLiveData(0)
    private val pageIdx by lazy { MutableLiveData(initialPageIdx) }
    private val pageNum = MutableLiveData(0)
    private val restartCount = MutableLiveData(0)
    private val canRedo = MutableLiveData(false)
    private val undoCount = MutableLiveData(0)
    private val redoCount = MutableLiveData(0)
    private val refreshCount = MutableLiveData(0)

    private var lastWritten = -1L
    private var emptyBmp: Bitmap? = null

    private var pageBmp: Bitmap? = null
    private var isDirty = false
    private fun notifyBitmapUpdate(newBmp : Bitmap) {
        isDirty = true
        lastWritten = getCurrentMills()

        pageBmp = newBmp
        lazySave()
    }


    private var canUndo = false
    private fun notifyUndoStateChanged(canUndo1: Boolean, canRedo1: Boolean) {
        val needRefresh = (canRedo.value != canRedo1)

        canUndo = canUndo1
        canRedo.value = canRedo1

        if(needRefresh) {
            refreshCount.value = refreshCount.value!! + 1
        }
    }

    private fun getCurrentMills() = (Date()).time

    private suspend fun savePage(pageIdx: Int, pageBmp: Bitmap) {
        bookIO.saveBitmap(book.getPage(pageIdx), pageBmp)
        withContext(Dispatchers.Main) {
            _book = book.assignNonEmpty(pageIdx)
        }
    }

    // same as savePage, but blocking in Main thread.
    private fun savePageInMain(pageIdx: Int, pageBmp: Bitmap) {
        bookIO.saveBitmap(book.getPage(pageIdx), pageBmp)
        _book = book.assignNonEmpty(pageIdx)
    }

    private val SAVE_INTERVAL_MILL = 5000L
    private fun lazySave() {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(SAVE_INTERVAL_MILL)
            if (isDirty && (getCurrentMills()- lastWritten) >= SAVE_INTERVAL_MILL) {
                isDirty = false
                val tmpBmp = BookActivity.bitmapLock.withLock {
                    val bmp = pageBmp!!
                    bmp.copy(bmp.config, false)
                }
                savePage(pageIdx.value!!, tmpBmp)
            }
        }
    }


    private fun ensureSave() {
        if (isDirty) {
            isDirty = false
            savePageInMain(pageIdx.value!!, pageBmp!!)
        }
    }

    private val handler = Handler()

    override fun onRestart() {
        super.onRestart()

        // Some time, it seems this call happens too early (before surface is available)
        // especially when back from "Share".
        // It had better handle those case in CanvasBoox properly, but just add delay here for a workaround.
        handler.postDelayed( {restartCount.value = restartCount.value!!+1}, 300)
    }

    override fun onStop() {
        ensureSave()
        super.onStop()
    }


    private fun share() {
        ensureSave()
        if (pageBmp == null) {
            return
        }
        val path = File.createTempFile("share", ".png", cacheDir)
        path.outputStream().use {
            pageBmp!!.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.flush()
        }
        val u = FileProvider.getUriForFile(this, applicationContext.packageName+".provider", path)
        shareImageUri(u)
    }


    /**
     * Shares the PNG image from Uri.
     * @param uri Uri of image to share.
     */
    private fun shareImageUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.type = "image/png"
        startActivity(intent)
    }
    private fun addNewPageAndGo() {
        ensureSave()
        if (emptyBmp == null) {
            pageBmp?.let { pbmp ->
                emptyBmp = Bitmap.createBitmap(pbmp.width, pbmp.height, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.WHITE)
                }

            }
        }

        _book = book.addPage()


        emptyBmp?.let {ebmp ->
            savePageInMain(pageNum.value!! - 1, ebmp)
        }
        pageIdx.value = pageNum.value!!-1
    }

    private fun gotoFirstPage() {
        ensureSave()
        pageIdx.value = 0
    }

    private fun gotoLastPage() {
        ensureSave()
        pageIdx.value = pageNum.value!!-1
    }

    private fun gotoPrevPage() {
        ensureSave()
        pageIdx.value?.let {
            if (it >= 1)
                pageIdx.value = it-1
        }
    }

    private fun gotoNextPage() {
        ensureSave()
        pageIdx.value?.let {
            pageIdx.value = it+1
        }
    }

    private fun gotoGridPage() {
        Intent(this, PageGridActivity::class.java).also {
            it.data = dirUrl
            startActivity(it)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            handlePageIdxArg(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            dirUrl = it.data!!

            handlePageIdxArg(it)
        }

        setContent {
            PngNoteTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Column {
                        var isEraser by remember { mutableStateOf(false) }
                        val idxState = pageIdx.observeAsState(0)
                        val pageNumState = pageNum.observeAsState(0)
                        val restartCountState = restartCount.observeAsState(0)
                        val canRedoState = canRedo.observeAsState(false)
                        val undoCountState = undoCount.observeAsState(0)
                        val redoCountState = redoCount.observeAsState(0)
                        val refreshCountState = refreshCount.observeAsState(0)

                        TopAppBar(title={
                            Row(modifier=Modifier.weight(5f), verticalAlignment = Alignment.CenterVertically) {
                                IconToggleButton(checked = !isEraser, onCheckedChange = {
                                    isEraser = false
                                } ) {
                                    val icon = if (isEraser) Icons.Outlined.Edit else Icons.Filled.Edit
                                    val alpha = if (isEraser) LocalContentAlpha.current*0.7f else LocalContentAlpha.current
                                    Icon(
                                        icon,
                                        contentDescription = "Pen",
                                        tint = LocalContentColor.current.copy(alpha = alpha)
                                    )
                                }

                                Spacer(modifier=Modifier.width(10.dp))
                                IconToggleButton(
                                    modifier=Modifier.size(26.dp),
                                    checked = isEraser, onCheckedChange = {
                                    isEraser = true
                                } ) {
                                    Image(painter = painterResource(if(isEraser) R.mipmap.eraser_button_selected else R.mipmap.eraser_button),
                                        contentDescription = "Eraser")
                                }
                                Spacer(modifier=Modifier.width(20.dp))

                                IconButton(onClick={
                                    if(canUndo) {
                                        undoCount.value = undoCount.value!!+1
                                    } else {
                                        showMessage("Not yet undo-able.")
                                    }
                                   }, enabled=true) {
                                    Icon(painter = painterResource(id = R.drawable.outline_undo), contentDescription = "Undo")
                                }
                                IconButton(onClick={ redoCount.value = redoCount.value!!+1  }, enabled=canRedoState.value) {
                                    Icon(painter = painterResource(id = R.drawable.outline_redo), contentDescription = "Redo")
                                }
                            }
                            Row(modifier=Modifier.weight(5f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                                IconButton(onClick= { gotoGridPage() }) {
                                    Icon(painter = painterResource(id = R.drawable.baseline_grid_view), contentDescription="Grid")
                                }
                                IconButton(modifier=Modifier.size(24.dp), onClick={ gotoFirstPage() }, enabled=idxState.value != 0) {
                                    Icon(painter = painterResource(id = R.drawable.outline_first_page), contentDescription = "First Page")
                                }
                                IconButton(modifier=Modifier.size(24.dp), onClick={ gotoPrevPage() }, enabled=idxState.value != 0) {
                                    Icon(painter = painterResource(id = R.drawable.baseline_chevron_left), contentDescription = "Prev Page")
                                }
                                val pidx = idxState.value+1
                                val pnum = pageNumState.value
                                Text("$pidx/$pnum")

                                val lastPage = idxState.value+1 == pageNumState.value
                                IconButton(modifier=Modifier.size(24.dp), onClick={ gotoNextPage() }, enabled=!lastPage) {
                                    Icon(painter = painterResource(id = R.drawable.baseline_chevron_right), contentDescription = "Next Page")
                                }
                                IconButton(modifier=Modifier.size(24.dp), onClick={ gotoLastPage() }, enabled=!lastPage) {
                                    Icon(painter = painterResource(id = R.drawable.outline_last_page), contentDescription = "Last Page")
                                }

                                IconButton(onClick={ addNewPageAndGo() }, enabled = lastPage) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Page")
                                }
                                IconButton(onClick={ share() }, enabled = true) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                ensureSave()
                                finish()
                            }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        })
                        BoxWithConstraints {
                            val initState = initCount.observeAsState(0)
                            AndroidView(modifier = Modifier.size(maxWidth, maxHeight),
                                factory = {context->
                                    val initBmp = bookIO.loadBitmapOrNull(book.getPage(pageIdx.value!!))
                                    val bgBmp = bookIO.loadBgOrNull(book)
                                    CanvasBoox(context, initBmp, bgBmp, initialPageIdx).apply {
                                        clipToOutline = true
                                        firstInit()
                                        setOnUpdateListener { notifyBitmapUpdate(it) }
                                        setOnUndoStateListener { undo, redo-> notifyUndoStateChanged(undo, redo) }
                                    }
                                },
                                update = {
                                    it.ensureInit(initState.value)
                                    it.penOrEraser(!isEraser)
                                    it.onPageIdx(idxState.value, bitmapLoader= {idx->
                                        bookIO.loadBitmapOrNull(book.getPage(idx)).also {
                                            isDirty = false
                                            pageBmp = it
                                        }
                                    })
                                    it.onRestart(restartCountState.value!!)
                                    it.undo(undoCountState.value)
                                    it.redo(redoCountState.value)
                                    it.refreshUI(refreshCountState.value)
                                }
                            )
                        }

                    }
                }
            }
        }

        // need to delay until onMeasure done. It might not be enough, but work for most of the time.
        handler.post {
            initCount.value = 1
        }

    }

    private fun handlePageIdxArg(intent: Intent) {
        val argPageIdx = intent.getIntExtra("PAGE_IDX", -1)
        if (argPageIdx != -1) {
            initialPageIdx = argPageIdx
        }
    }
}

