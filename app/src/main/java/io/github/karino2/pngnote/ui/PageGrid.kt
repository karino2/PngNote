package io.github.karino2.pngnote.ui

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Page(val title: String, val thumbnail: Bitmap, val bgThumbnail: Bitmap?)
{
    val isEmpty = title == ""
}

class PageGridData(private val pages: List<Page>) {
    companion object {
        val blankBitmap: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(
            android.graphics.Color.LTGRAY) }
    }

    val colNum = 4

    val rowNum : Int
        get() = (pages.size + 3)/colNum

    private fun makeBlankPage() = Page("", blankBitmap, null)

    fun getPage(row: Int, col: Int) : Page {
        val index = toIndex(row, col)
        return if (index < pages.size) pages[index] else makeBlankPage()
    }

    fun toIndex(row: Int, col: Int) = row * colNum + col
}

@Composable
fun OnePage(pageSize : Pair<Dp, Dp>, title: String, thumbnail: ImageBitmap, bgImage: ImageBitmap?, onOpenPage : ()->Unit) {
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
        Text(title, fontSize = 20.sp)
    }
}

@Composable
fun PageGrid(pages: List<Page>, pageSize : Pair<Dp, Dp>, onOpenPage: (Int)->Unit) {
    val pageGrid = PageGridData(pages)
    Column(
        modifier = Modifier
            .padding(10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        0.until(pageGrid.rowNum).forEach { rowIdx ->
            PageGridRow(
                pageSize,
                rowIdx,
                pageGrid,
                onOpenPage = onOpenPage)
        }
    }
}
@Composable
fun PageGridRow(pageSize : Pair<Dp, Dp>, rowIdx :Int, pageGrid: PageGridData, onOpenPage: (Int)->Unit) {
    Row {
        0.until(pageGrid.colNum).forEach {colIdx->
            val page = pageGrid.getPage(rowIdx, colIdx)
            if (page.isEmpty) {
                Card(modifier = Modifier.weight(1f)) {}
            } else {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(5.dp),
                    border = BorderStroke(2.dp, Color.Black)
                ) {
                    OnePage(pageSize, page.title, page.thumbnail.asImageBitmap(), page.bgThumbnail?.asImageBitmap(), onOpenPage ={ onOpenPage(pageGrid.toIndex(rowIdx, colIdx)) } )
                }
            }
        }
    }
}

