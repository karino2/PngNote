package io.github.karino2.pngnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.*
import androidx.compose.material.ButtonDefaults.textButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


private val LightColorPalette = lightColors(
    primary = Color.White,
    primaryVariant = Color.LightGray,
    secondary = Teal200,
    onPrimary = Color.Black

    /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

@Composable
fun PngNoteTheme(content: @Composable() () -> Unit) {
    val colors = LightColorPalette

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

@Composable
fun booxTextButtonColors(): ButtonColors = textButtonColors(backgroundColor = Color.White,
    contentColor = Color.Black,
    disabledContentColor = Color.LightGray
)