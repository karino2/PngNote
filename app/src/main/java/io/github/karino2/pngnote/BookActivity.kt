package io.github.karino2.pngnote

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    private val book by lazy { bookIO.loadBook(bookDir) }


    private val initCount = MutableLiveData(0)
    private val refreshCount = MutableLiveData(0)
    private val pageIdx = MutableLiveData(0)

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

    override fun onStop() {
        if (isDirty) {
            isDirty = false
            bookIO.saveBitmap(book.getPage(pageIdx.value!!), pageBmp!!)
        }
        super.onStop()
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

                        TopAppBar(title={
                            Text("Png Note")
                            Spacer(modifier=Modifier.width(20.dp))
                            RadioButton(selected = !isEraser, onClick = {
                                isEraser = false
                                refreshCount.value = refreshCount.value!!+1
                            })
                            Text("Pen")

                            Spacer(modifier=Modifier.width(10.dp))
                            RadioButton(selected = isEraser, onClick = {
                                isEraser = true
                                refreshCount.value = refreshCount.value!!+1
                            })
                            Text("Eraser")
                        })
                        BoxWithConstraints {
                            val initState = initCount.observeAsState(0)
                            val refreshState = refreshCount.observeAsState(0)
                            val idxState = pageIdx.observeAsState(0)
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
                                    it.refreshUI(refreshState.value)
                                    it.penOrEraser(!isEraser)
                                    it.onPageIdx(idxState.value, bitmapLoader= {idx-> bookIO.loadBitmap(book.getPage(idx))})
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

