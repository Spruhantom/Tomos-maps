package uk.co.tomosmaps

import androidx.compose.ui.window.singleWindowApplication
import org.maplibre.compose.desktop.ProvideMapPresentationHost
import org.maplibre.compose.desktop.rememberAwtComposeMapPresentationHost
import uk.co.tomosmaps.navigation.createOfflineValhallaRuntime
import uk.co.tomosmaps.offline.RegionalPackageBuildConfig
import uk.co.tomosmaps.offline.createRegionalPackageTransfer
import java.io.File

fun main() {
    val storageDirectory = File(
        System.getProperty("user.home"),
        ".local/share/tomos-maps/regional-packages"
    ).absolutePath
    val regionalTransfer = createRegionalPackageTransfer(
        storageDirectory = storageDirectory,
        pinnedEd25519PublicKeyBase64 = RegionalPackageBuildConfig.PINNED_ED25519_PUBLIC_KEY_BASE64,
    )
    val offlineRuntime = createOfflineValhallaRuntime(storageDirectory)
    singleWindowApplication(title = "TomOS Maps") {
        ProvideMapPresentationHost(
            host = rememberAwtComposeMapPresentationHost(window)
        ) {
            App(
                regionalPackageTransfer = regionalTransfer,
                offlineValhallaRuntime = offlineRuntime,
            )
        }
    }
}
