package app.servante.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.servante.ui.theme.ServanteTheme
import app.servante.ui.theme.Sizes

/**
 * The desktop entry point.
 *
 * Minimal on purpose: it exists so the window, the theme and the packaging have something real to work
 * with while the screens are built. The composition root that wires each tool to its screen
 * (`docs/adr/0004`) replaces the body of [ServanteApp] as the screens land.
 */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Servante",
        state = rememberWindowState(width = INITIAL_WIDTH, height = INITIAL_HEIGHT),
    ) {
        ServanteApp()
    }
}

private val INITIAL_WIDTH = 1280.dp
private val INITIAL_HEIGHT = 800.dp

@Composable
private fun ServanteApp() {
    ServanteTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ServanteTheme.colors.background)
                .padding(Sizes.mainPaddingHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ServanteTheme.strings.welcomeHeadline,
                style = ServanteTheme.typography.screenTitle.copy(color = ServanteTheme.colors.text),
            )
        }
    }
}
