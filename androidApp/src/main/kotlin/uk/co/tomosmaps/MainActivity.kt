package uk.co.tomosmaps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import uk.co.tomosmaps.offline.RegionalPackageBuildConfig
import uk.co.tomosmaps.offline.createRegionalPackageTransfer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val regionalTransfer = createRegionalPackageTransfer(
            storageDirectory = filesDir.resolve("regional-packages").absolutePath,
            pinnedEd25519PublicKeyBase64 = RegionalPackageBuildConfig.PINNED_ED25519_PUBLIC_KEY_BASE64,
        )
        setContent { App(regionalPackageTransfer = regionalTransfer) }
    }
}
