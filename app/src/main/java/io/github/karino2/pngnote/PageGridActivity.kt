package io.github.karino2.pngnote

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import io.github.karino2.pngnote.ui.theme.PngNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class Page( private val page: FastFile? ) {
    companion object {
        val blankBitmap: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(
            android.graphics.Color.LTGRAY) }
    }

    val isEmpty = (page == null)
    val thumbnail = MutableLiveData(blankBitmap)

    suspend fun requestLoad(bookIO: BookIO) {
        val bmpfile = page ?: return

        val bitmap = bookIO.loadPageThumbnail(bmpfile)
        withContext(Dispatchers.Main) {
            thumbnail.value = bitmap
        }
    }

}

class PageGrid(private val bookIO: BookIO, private val pageFiles: List<FastFile>, val bgImage: ImageBitmap?) {
    val colNum = 4

    val rowNum : Int
        get() = (pageFiles.size + 3)/colNum

    private val pages = pageFiles.map { Page(it) }

    fun getPage(row: Int, col: Int) : Page {
        val index = row*colNum + col
        return if (index < pages.size) pages[index] else Page(null)
    }

    val thumbnails = pages.map { it.thumbnail }

    suspend fun requestLoadThumbnails() {
        pages.forEach { it.requestLoad(bookIO) }
    }
}

class PageGridActivity: ComponentActivity() {
    private lateinit var dirUrl : Uri
    private val bookDir by lazy {
        FastFile.fromTreeUri(this, dirUrl)
    }

    private val bookIO by lazy { BookIO(contentResolver) }

    private var _book : Book? = null

    private val book : Book
        get() {
            _book?.let { return it }
            bookIO.loadBook(bookDir).also {
                _book = it
                return it
            }
        }

    private val bgImage by lazy { bookIO.loadBgForGrid(book.bookDir)?.asImageBitmap() }

    private val pageGrid by lazy {
        PageGrid(bookIO, book.pages, bgImage)
    }
    private val pageSizeDP by lazy {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // about 1/4 of 80%〜90%.

        val height = (metrics.heightPixels*0.20/metrics.density).dp
        val width = (metrics.widthPixels*0.225/metrics.density).dp

        Pair(width, height)
    }

    private fun requestLoadPages() {
        lifecycleScope.launch(Dispatchers.IO) {
            pageGrid.requestLoadThumbnails()
        }
    }

    fun openPage(pageIdx: Int) {
        Intent(this, BookActivity::class.java).also {
            it.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            it.data = bookDir.uri
            it.putExtra("PAGE_IDX", pageIdx)

            startActivity(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent?.let {
            dirUrl = it.data!!
        }

        val thumbnails = pageGrid.thumbnails

        setContent {
            PngNoteTheme {
                Column {
                    TopAppBar(title={Text(book.name)},
                        navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    })
                    Column(modifier= Modifier
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())) {
                        0.until(pageGrid.rowNum).forEach { rowIdx ->
                            PageGridRow(pageSizeDP, rowIdx, pageGrid, thumbnails, onOpenPage = {pageIdx-> openPage(pageIdx) })
                        }
                    }
                }
            }
        }

        requestLoadPages()
    }
}

@Composable
fun PageGridRow(pageSize : Pair<Dp, Dp>, rowIdx :Int, pageGrid: PageGrid, thumbnails: List<MutableLiveData<Bitmap>>, onOpenPage: (Int)->Unit) {
    Row {
        0.until(pageGrid.colNum).forEach {colIdx->
            val pageIdx = rowIdx*pageGrid.colNum+colIdx
            val page = pageGrid.getPage(rowIdx, colIdx)
            if (page.isEmpty) {
                Card(modifier = Modifier.weight(1f)) {}
            } else {
                val thumbnailState = thumbnails[pageIdx].observeAsState()
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(5.dp),
                    border = BorderStroke(2.dp, Color.Black)
                ) {
                    OnePage(pageSize, pageIdx, page, thumbnailState.value!!.asImageBitmap(), pageGrid.bgImage, onOpenPage ={ onOpenPage(pageIdx) } )
                }
            }
        }
    }
}

@Composable
fun OnePage(pageSize : Pair<Dp, Dp>, pageNum: Int, page: Page, thumbnail: ImageBitmap, bgImage: ImageBitmap?, onOpenPage : ()->Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier= Modifier
        .clickable(onClick = onOpenPage)) {
        Canvas(modifier= Modifier
            .size(pageSize.first, pageSize.second)
            .padding(2.dp, 2.dp)) {
            val blendMode = bgImage?.let { bg->
                drawImage(bg)
                BlendMode.Multiply
            } ?: BlendMode.SrcOver
            drawImage(thumbnail, blendMode = blendMode)
        }
        Text((pageNum+1).toString(), fontSize = 20.sp)
    }
}
