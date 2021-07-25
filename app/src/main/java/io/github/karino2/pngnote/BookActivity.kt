package io.github.karino2.pngnote

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
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
import java.util.*

class BookActivity : ComponentActivity() {
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
        bookName.value = bookDir.name
        bookIO.loadBook(bookDir).also {
            _book = it
            return it
        }
    }


    private val initCount = MutableLiveData(0)
    private val pageIdx = MutableLiveData(0)
    private val pageNum = MutableLiveData(0)
    private val bookName = MutableLiveData("(No name)")
    private val restartCount = MutableLiveData(0)

    private var lastWritten = -1L

    private var pageBmp: Bitmap? = null
    private var isDirty = false
    private fun notifyBitmapUpdate(newBmp : Bitmap) {
        isDirty = true
        lastWritten = getCurrentMills()
        pageBmp = newBmp
        lazySave()
    }

    private fun getCurrentMills() = (Date()).time


    private val SAVE_INTERVAL_MILL = 5000L
    private fun lazySave() {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(SAVE_INTERVAL_MILL)
            if ((getCurrentMills()- lastWritten) >= SAVE_INTERVAL_MILL) {
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
                        val bookNameState = bookName.observeAsState("")
                        val idxState = pageIdx.observeAsState(0)
                        val pageNumState = pageNum.observeAsState(0)
                        val restartCountState = restartCount.observeAsState(0)

                        TopAppBar(title={
                            Row(modifier=Modifier.weight(3f)) {
                                Text(bookNameState.value)
                            }
                            Row(modifier=Modifier.weight(4f)) {
                                RadioButton(selected = !isEraser, onClick = {
                                    isEraser = false
                                })
                                Text("Pen")

                                Spacer(modifier=Modifier.width(10.dp))
                                RadioButton(selected = isEraser, onClick = {
                                    isEraser = true
                                })
                                Text("Eraser")
                            }
                            Row(modifier=Modifier.weight(3f), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick={ gotoPrevPage() }, enabled=idxState.value != 0) {
                                    Icon(painter = painterResource(id = R.drawable.baseline_chevron_left), contentDescription = "Prev Page")
                                }
                                val pidx = idxState.value+1
                                val pnum = pageNumState.value
                                Text("$pidx/$pnum")

                                val lastPage = idxState.value+1 == pageNumState.value
                                IconButton(onClick={ gotoNextPage() }, enabled=!lastPage) {
                                    Icon(painter = painterResource(id = R.drawable.baseline_chevron_right), contentDescription = "Next Page")
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
                                    }
                                },
                                update = {
                                    it.ensureInit(initState.value)
                                    it.penOrEraser(!isEraser)
                                    it.onPageIdx(idxState.value, bitmapLoader= {idx-> bookIO.loadBitmapOrNull(book.getPage(idx))})
                                    it.onRestart(restartCountState.value!!)
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

