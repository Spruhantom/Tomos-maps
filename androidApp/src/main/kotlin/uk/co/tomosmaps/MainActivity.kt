package uk.co.tomosmaps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import uk.co.tomosmaps.navigation.createOfflineValhallaRuntime
import uk.co.tomosmaps.offline.RegionalPackageBuildConfig
import uk.co.tomosmaps.offline.createRegionalPackageTransfer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageDirectory = filesDir.resolve("regional-packages").absolutePath
        val regionalTransfer = createRegionalPackageTransfer(
            storageDirectory = storageDirectory,
            pinnedEd25519PublicKeyBase64 = RegionalPackageBuildConfig.PINNED_ED25519_PUBLIC_KEY_BASE64,
        )
        val offlineRuntime = createOfflineValhallaRuntime(storageDirectory, applicationContext)
        setContent {
            App(
                regionalPackageTransfer = regionalTransfer,
                offlineValhallaRuntime = offlineRuntime,
                regionalPackageStorageDirectory = storageDirectory,
            )
        }
    }
}

