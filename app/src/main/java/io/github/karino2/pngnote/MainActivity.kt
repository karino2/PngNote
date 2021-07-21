package io.github.karino2.pngnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.MutableLiveData
import io.github.karino2.pngnote.ui.theme.PngNoteTheme

class MainActivity : ComponentActivity() {
    private val initCount = MutableLiveData(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PngNoteTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Column {
                        TopAppBar(title={ Text("Png Note") })
                        BoxWithConstraints {
                            val initState = initCount.observeAsState(0)
                            AndroidView(modifier = Modifier.size(maxWidth, maxHeight),
                                factory = {context->
                                    CanvasBoox(context, null).apply {
                                        firstInit()
                                    }
                                },
                                update = {
                                    it.ensureInit(initState.value)
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

