package io.github.karino2.pngnote

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
import androidx.lifecycle.MutableLiveData
import io.github.karino2.pngnote.ui.theme.PngNoteTheme

class MainActivity : ComponentActivity() {
    private val initCount = MutableLiveData(0)
    private val refreshCount = MutableLiveData(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                            AndroidView(modifier = Modifier.size(maxWidth, maxHeight),
                                factory = {context->
                                    CanvasBoox(context, null).apply {
                                        firstInit()
                                    }
                                },
                                update = {
                                    it.ensureInit(initState.value)
                                    it.refreshUI(refreshState.value)
                                    it.penOrEraser(!isEraser)
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

