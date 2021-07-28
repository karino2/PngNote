package io.github.karino2.pngnote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.github.karino2.pngnote.ui.theme.PngNoteTheme
import io.github.karino2.pngnote.ui.theme.booxTextButtonColors

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

    private fun reloadBookList(url: Uri) {
        files.value = listFiles(url)
    }

    private fun listFiles(url: Uri): List<DocumentFile> {
        val rootDir = DocumentFile.fromTreeUri(this, url) ?: throw Exception("Can't open dir")
        if (!rootDir.isDirectory)
            throw Exception("Not directory")
        return rootDir.listFiles()
            .filter { it.isDirectory }
            .sortedByDescending { it.name }
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
                    })
                    if (showDialog.value) {
                        NewBookPopup(onNewBook = { addNewBook(it) }, onDismiss= { showDialog.value = false })
                    }
                    BookList(files, gotoBook = { bookDir ->
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
                        .border(width=1.dp, MaterialTheme.colors.onPrimary)
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



@Composable
fun BookList(bookDirs: LiveData<List<DocumentFile>>, gotoBook : (dir: DocumentFile)->Unit) {
    val bookListState = bookDirs.observeAsState(emptyList())

    Column(modifier= Modifier
        .padding(10.dp)
        .verticalScroll(rememberScrollState())) {
        bookListState.value.forEach { dir->
            Book(dir, onOpenBook = { gotoBook(dir) })
        }
    }
}

@Composable
fun Book(deckDir: DocumentFile, onOpenBook : ()->Unit) {
    Card(border= BorderStroke(2.dp, Color.Black)) {
        Row(modifier= Modifier
            .clickable(onClick = onOpenBook)
            .padding(5.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(deckDir.name!!, fontSize = 20.sp, modifier=Modifier.weight(9f))
        }
    }

}
