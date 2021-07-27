package io.github.karino2.pngnote

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import io.github.karino2.pngnote.ui.theme.PngNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class BookActivity : ComponentActivity() {
    companion object {
        val bitmapLock = java.util.concurrent.locks.ReentrantLock()
    }
    private lateinit var dirUrl : Uri
    private val bookDir by lazy {
        DocumentFile.fromTreeUri(this, dirUrl) ?: throw Exception("Cant open dir.")
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
    private val pageIdx = MutableLiveData(0)
    private val pageNum = MutableLiveData(0)
    private val restartCount = MutableLiveData(0)
    private val canRedo = MutableLiveData(false)
    private val undoCount = MutableLiveData(0)
    private val redoCount = MutableLiveData(0)
    private val refreshCount = MutableLiveData(0)

    private var lastWritten = -1L

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


    private val SAVE_INTERVAL_MILL = 5000L
    private fun lazySave() {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(SAVE_INTERVAL_MILL)
            if (isDirty && (getCurrentMills()- lastWritten) >= SAVE_INTERVAL_MILL) {
                isDirty = false
                bookIO.saveBitmap(book.getPage(pageIdx.value!!), pageBmp!!)
            }
        }
    }


    private fun ensureSave() {
        if (isDirty) {
            isDirty = false
            bookIO.saveBitmap(book.getPage(pageIdx.value!!), pageBmp!!)
        }
    }

    override fun onRestart() {
        super.onRestart()
        restartCount.value = restartCount.value!!+1
    }

    override fun onStop() {
        ensureSave()
        super.onStop()
    }



    private fun addNewPageAndGo() {
        ensureSave()
        _book = book.addPage()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            dirUrl = it.data!!
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
                                RadioButton(selected = !isEraser, onClick = {
                                    isEraser = false
                                })
                                Text("Pen")

                                Spacer(modifier=Modifier.width(10.dp))
                                RadioButton(selected = isEraser, onClick = {
                                    isEraser = true
                                })
                                Text("Eraser")
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
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        })
                        BoxWithConstraints {
                            val initState = initCount.observeAsState(0)
                            AndroidView(modifier = Modifier.size(maxWidth, maxHeight),
                                factory = {context->
                                    val initBmp =bookIO.loadBitmapOrNull(book.getPage(pageIdx.value!!))
                                    CanvasBoox(context, initBmp).apply {
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

        initCount.value = 1

    }
}

