package io.github.karino2.pngnote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.karino2.pngnote.ui.Page
import io.github.karino2.pngnote.ui.PageGrid
import io.github.karino2.pngnote.ui.PageGridData
import io.github.karino2.pngnote.ui.theme.PngNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class PageGridActivity: ComponentActivity() {
    companion object {
        fun displayMetricsTo4GridSize(metrics: DisplayMetrics) : Pair<Dp, Dp> {
            // about 1/4 of 80%〜90%.

            val height = (metrics.heightPixels*0.20/metrics.density).dp
            val width = (metrics.widthPixels*0.225/metrics.density).dp

            return Pair(width, height)
        }
    }

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

    private val bgImage by lazy { bookIO.loadBgForGrid(book.bookDir) }
    private val pageList by lazy {
        mutableStateOf(book.pages.mapIndexed { idx, _ ->
            Page(
                (idx + 1).toString(),
                PageGridData.blankBitmap,
                bgImage
            )
        })
    }

    private val pageSizeDP by lazy {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        displayMetricsTo4GridSize(metrics)
    }

    private fun requestLoadPages() {
        lifecycleScope.launch(Dispatchers.IO) {
            book.pages.forEachIndexed { idx, bmpfile ->
                val bitmap = bookIO.loadPageThumbnail(bmpfile)
                withContext(Dispatchers.Main) {
                    pageList.value = pageList.value.mapIndexed {idx2, page -> if(idx==idx2) page.copy(thumbnail = bitmap) else page }
                }
            }
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

        setContent {
            PngNoteTheme {
                Column {
                    TopAppBar(title={Text(book.name)},
                        navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    })
                    PageGrid(pageList.value, pageSizeDP, onOpenPage = { pageIdx -> openPage(pageIdx) })
                }
            }
        }

        requestLoadPages()
    }

}

