package uk.co.tomosmaps

import androidx.compose.ui.window.singleWindowApplication
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost

fun main() = singleWindowApplication(title = "TomOS Maps") {
    ProvideMapPresentationHost(
        host = rememberAwtComposeMapPresentationHost(window)
    ) {
        App()
    }
}
