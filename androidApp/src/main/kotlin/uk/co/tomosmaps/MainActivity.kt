package uk.co.tomosmaps

import android.os.Bundle
import android.os.Build
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import uk.co.tomosmaps.navigation.createOfflineValhallaRuntime
import uk.co.tomosmaps.offline.RegionalPackageBuildConfig
import uk.co.tomosmaps.offline.createRegionalPackageTransfer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageRoot = filesDir.resolve("regional-packages")
        val storageDirectory = storageRoot.absolutePath
        installBundledValhallaEngine(storageRoot)
        val regionalTransfer = createRegionalPackageTransfer(
            storageDirectory = storageDirectory,
            pinnedEd25519PublicKeyBase64 = RegionalPackageBuildConfig.PINNED_ED25519_PUBLIC_KEY_BASE64,
        )
        val offlineRuntime = createOfflineValhallaRuntime(storageDirectory)
        setContent {
            App(
                regionalPackageTransfer = regionalTransfer,
                offlineValhallaRuntime = offlineRuntime,
            )
        }
    }
}

private fun MainActivity.installBundledValhallaEngine(storageRoot: File) {
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return
    val assetPath = "engines/$abi/valhalla_service"
    val destination = File(storageRoot, assetPath)
    if (destination.isFile && destination.canExecute()) {
        destination.setWritable(false, true)
        return
    }
    runCatching {
        val available = assets.list("engines/$abi").orEmpty()
        if ("valhalla_service" !in available) return
        destination.parentFile?.mkdirs()
        assets.open(assetPath).use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        destination.setExecutable(true, true)
        destination.setWritable(false, true)
    }
}
