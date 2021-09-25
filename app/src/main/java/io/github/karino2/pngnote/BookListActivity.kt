package io.github.karino2.pngnote

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.ButtonDefaults.textButtonColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.*
import io.github.karino2.pngnote.ui.theme.PngNoteTheme
import io.github.karino2.pngnote.ui.theme.booxTextButtonColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Thumbnail(val page: Bitmap, val bg: Bitmap?)


class BookListActivity : ComponentActivity() {
    private var _url : Uri? = null

    private val getRootDirUrl = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        // if cancel, null coming.
        uri?.let {
            contentResolver.takePersistableUriPermission(it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            writeLastUri(it)
            openRootDir(it)
        }
    }

    private val lastUri: Uri?
        get() = BookList.lastUriStr(this)?.let { Uri.parse(it) }

    private fun writeLastUri(uri: Uri) = BookList.writeLastUriStr(this, uri.toString())

    private fun showMessage(msg: String) = BookList.showMessage(this, msg)

    private fun openRootDir(url: Uri) {
        _url = url
        reloadBookList(url)
    }

    private val files = MutableLiveData(emptyList<DocumentFile>())
    private val thumbnails =  Transformations.switchMap(files) { flist ->
        liveData {
            emit(flist.map { Thumbnail(blankBitmap, null) })
            withContext(lifecycleScope.coroutineContext + Dispatchers.IO) {
                val thumbs = flist.map {
                    val page = bookIO.loadThumbnail(it) ?: blankBitmap
                    val bg = bookIO.loadBgThumbnail(it)
                    Thumbnail(page, bg)
                }
                withContext(Dispatchers.Main) {
                    emit(thumbs)
                }
            }
        }
    }

    private fun reloadBookList(url: Uri) {
        listFiles(url).also { flist->
            files.value = flist
        }
    }

    private fun listFiles(url: Uri): List<DocumentFile> {
        val rootDir = DocumentFile.fromTreeUri(this, url) ?: throw Exception("Can't open dir")
        if (!rootDir.isDirectory)
            throw Exception("Not directory")
        return rootDir.listFiles()
            .filter { it.isDirectory }
            .sortedByDescending { it.name }
    }

    private val bookSizeDP by lazy {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        // about half of 80%〜90%.

        val height = (metrics.heightPixels*0.40/metrics.density).dp
        val width = (metrics.widthPixels*0.45/metrics.density).dp

        Pair(width, height)
    }

    private val bookIO by lazy { BookIO(contentResolver) }

    override fun onRestart() {
        super.onRestart()

        // return from other activity, etc.
        if(true == files.value?.isNotEmpty()) {
            reloadBookList(_url!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PngNoteTheme {
                Column {
                    val showDialog = rememberSaveable { mutableStateOf(false) }
                    TopAppBar(title={Text("Book List")}, actions = {
                        IconButton(onClick={ showDialog.value = true }) {
                            Icon(Icons.Filled.Add, "New Book")
                        }
                        IconButton(onClick={ getRootDirUrl.launch(null) }) {
                            Icon(Icons.Filled.Settings, "Settings")
                        }
                    }, navigationIcon = {
                        Image(painterResource(id = R.mipmap.ic_launcher), null)
                    })
                    if (showDialog.value) {
                        NewBookPopup(onNewBook = { addNewBook(it) }, onDismiss= { showDialog.value = false })
                    }
                    BookList(files, thumbnails, bookSizeDP,
                        gotoBook = { bookDir ->
                        Intent(this@BookListActivity, BookActivity::class.java).also {
                            it.data = bookDir.uri
                            startActivity(it)
                        }
                    })
                }
            }
        }

        try {
            lastUri?.let {
                return openRootDir(it)
            }
        } catch(_: Exception) {
            showMessage("Can't open dir. Please re-open.")
        }
        getRootDirUrl.launch(null)
    }

    private fun addNewBook(newBookName: String) {
        val rootDir = _url?.let { DocumentFile.fromTreeUri(this, it) } ?: throw Exception("Can't open dir")
        try {
            rootDir.createDirectory(newBookName)
            openRootDir(_url!!)
        } catch(_: Exception) {
            showMessage("Can't create book directory ($newBookName).")
        }
    }

}


@Composable
fun NewBookPopup(onNewBook : (bookName: String)->Unit, onDismiss: ()->Unit) {
    var textState by remember { mutableStateOf("") }
    val requester = FocusRequester()
    val buttonColors = booxTextButtonColors()
    AlertDialog(
        modifier=Modifier.border(width=1.dp, MaterialTheme.colors.onPrimary),
        onDismissRequest = onDismiss,
        text = {
            Column {
                TextField(value = textState, onValueChange={textState = it},
                    modifier= Modifier
                        .border(width = 1.dp, MaterialTheme.colors.onPrimary)
                        .fillMaxWidth()
                        .focusRequester(requester),
                    placeholder = { Text("New book name")})
                DisposableEffect(Unit) {
                    requester.requestFocus()
                    onDispose {}
                }
            }
        },
        confirmButton = {
            TextButton(modifier=Modifier.border(width=1.dp, MaterialTheme.colors.onPrimary),
                colors = buttonColors, onClick= {
                onDismiss()
                if(textState != "") {
                    onNewBook(textState)
                }
            }) {
                Text("CREATE")
            }
        },
        dismissButton = {
            TextButton(modifier=Modifier.border(width=1.dp, MaterialTheme.colors.onPrimary),
                colors = buttonColors, onClick= onDismiss) {
                Text("CANCEL")
                Spacer(modifier = Modifier.width(5.dp))
            }
        }

    )
}

val blankBitmap: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(
    android.graphics.Color.LTGRAY) }


@Composable
fun BookList(bookDirs: LiveData<List<DocumentFile>>, thumbnails: LiveData<List<Thumbnail>>, bookSize : Pair<Dp, Dp>,  gotoBook : (dir: DocumentFile)->Unit) {
    val bookListState = bookDirs.observeAsState(emptyList())
    val thumbnailListState = thumbnails.observeAsState(emptyList())

    Column(modifier= Modifier
        .padding(10.dp)
        .verticalScroll(rememberScrollState())) {
        val rowNum = (bookListState.value.size+1)/2
        0.until(rowNum).forEach { rowIdx ->
            TwoBook(bookListState.value, thumbnailListState.value,rowIdx*2, bookSize, gotoBook)
        }
    }
}

@Composable
fun TwoBook(books: List<DocumentFile>, thumbnails: List<Thumbnail>, leftIdx: Int, bookSize : Pair<Dp, Dp>, gotoBook : (dir: DocumentFile)->Unit) {
    Row {
        Card(modifier= Modifier
            .weight(1f)
            .padding(5.dp), border= BorderStroke(2.dp, Color.Black)) {
            val book =books[leftIdx]
            Book(book, bookSize, thumbnails[leftIdx], onOpenBook = { gotoBook(book) })
        }
        if (leftIdx+1 < books.size) {
            Card(modifier= Modifier
                .weight(1f)
                .padding(5.dp), border= BorderStroke(2.dp, Color.Black)) {
                val book =books[leftIdx+1]
                Book(book, bookSize, thumbnails[leftIdx+1], onOpenBook = { gotoBook(book) })
            }
        } else {
            Card(modifier=Modifier.weight(1f)) {}
        }
    }
}

@Composable
fun Book(bookDir: DocumentFile, bookSize : Pair<Dp, Dp>, thumbnail: Thumbnail, onOpenBook : ()->Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier= Modifier
        .clickable(onClick = onOpenBook)) {
        Canvas(modifier= Modifier
            .size(bookSize.first, bookSize.second)
            .padding(5.dp, 10.dp)) {
            val blendMode = thumbnail.bg?.let { bg->
                drawImage(bg.asImageBitmap())
                BlendMode.Multiply
            } ?: BlendMode.SrcOver
            drawImage(thumbnail.page.asImageBitmap(), blendMode=blendMode)
        }
        Text(bookDir.name!!, fontSize = 20.sp)
    }
}
