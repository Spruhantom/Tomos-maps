package uk.co.tomosmaps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import uk.co.tomosmaps.navigation.createOfflineValhallaRuntime
import uk.co.tomosmaps.navigation.TomosFerrostarAndroidNavigation
import uk.co.tomosmaps.navigation.ValhallaRoutingClient
import uk.co.tomosmaps.navigation.RouteCoordinate
import uk.co.tomosmaps.offline.RegionalPackageBuildConfig
import uk.co.tomosmaps.offline.createRegionalPackageTransfer

class MainActivity : ComponentActivity() {
    private var ferrostarNavigation: TomosFerrostarAndroidNavigation? = null
    private var routingClient: ValhallaRoutingClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storageDirectory = filesDir.resolve("regional-packages").absolutePath
        val regionalTransfer = createRegionalPackageTransfer(
            storageDirectory = storageDirectory,
            pinnedEd25519PublicKeyBase64 = RegionalPackageBuildConfig.PINNED_ED25519_PUBLIC_KEY_BASE64,
        )
        val offlineRuntime = createOfflineValhallaRuntime(storageDirectory, applicationContext)
        val localRoutingClient = ValhallaRoutingClient()
        val ferrostar = TomosFerrostarAndroidNavigation(applicationContext, localRoutingClient, offlineRuntime)
        routingClient = localRoutingClient
        ferrostarNavigation = ferrostar
        setContent {
            val windowLayoutInfo by WindowInfoTracker.getOrCreate(this)
                .windowLayoutInfo(this)
                .collectAsState(initial = WindowLayoutInfo(emptyList()))
            val foldingFeature = windowLayoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            val foldPosture = when {
                foldingFeature == null -> "FLAT"
                foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                    foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL -> "TABLETOP"
                foldingFeature.state == FoldingFeature.State.HALF_OPENED &&
                    foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL -> "BOOK"
                foldingFeature.isSeparating -> "SEPARATING"
                else -> "FLAT"
            }
            App(
                regionalPackageTransfer = regionalTransfer,
                offlineValhallaRuntime = offlineRuntime,
                regionalPackageStorageDirectory = storageDirectory,
                foldPosture = foldPosture,
                onStartOfflineNavigation = { region, origin, destination ->
                    ferrostar.calculateAndStart(region, origin, destination)
                },
                onStopOfflineNavigation = { ferrostar.stopNavigation() },
                enableSharedOfflineRerouting = false,
            )
        }
    }

    override fun onDestroy() {
        ferrostarNavigation?.close()
        ferrostarNavigation = null
        routingClient?.close()
        routingClient = null
        super.onDestroy()
    }
}

