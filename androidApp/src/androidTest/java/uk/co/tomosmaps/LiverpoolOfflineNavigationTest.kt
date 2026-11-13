package uk.co.tomosmaps

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.tomosmaps.navigation.RouteCoordinate
import uk.co.tomosmaps.navigation.ValhallaCosting
import uk.co.tomosmaps.navigation.ValhallaRouteRequest
import uk.co.tomosmaps.navigation.ValhallaRouteResult
import uk.co.tomosmaps.navigation.ValhallaRoutingClient
import uk.co.tomosmaps.navigation.createOfflineValhallaRuntime
import uk.co.tomosmaps.offline.createRegionalOfflineMapStyle
import uk.co.tomosmaps.offline.createRegionalPackageArchiveInstaller
import uk.co.tomosmaps.offline.createRegionalPackageTransfer
import uk.co.tomosmaps.offline.regionalMapDataFileUri
import java.io.File

@RunWith(AndroidJUnit4::class)
class LiverpoolOfflineNavigationTest {
    @Test
    fun installsSearchesRendersAndRoutesWithoutInternet() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            "The end-to-end test must run with airplane mode enabled.",
            1,
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0),
        )

        val root = File(context.filesDir, "regional-packages").apply {
            deleteRecursively()
            mkdirs()
        }
        val packageZip = File(root, "liverpool-region.zip")
        context.assets.open("liverpool-region.zip").use { input ->
            packageZip.outputStream().use { output -> input.copyTo(output) }
        }
        val manifestJson = context.assets.open("liverpool-region.manifest.json")
            .bufferedReader().use { it.readText() }
        val metadataText = context.assets.open("liverpool-region.metadata.json")
            .bufferedReader().use { it.readText() }
        val manifestDigest = Regex("\\\"manifestSha256\\\"\\s*:\\s*\\\"([a-fA-F0-9]{64})\\\"")
            .find(metadataText)?.groupValues?.get(1)
            ?: error("The regional package metadata has no valid manifest SHA-256.")

        val installer = createRegionalPackageArchiveInstaller(root.absolutePath)
        val installed = installer.install(
            packageZip.absolutePath,
            manifestJson,
            manifestDigest,
        ).getOrThrow()
        val transfer = createRegionalPackageTransfer(root.absolutePath, "")
        val installedPackages = transfer.installedPackages()
        assertEquals(1, installedPackages.size)
        assertEquals("liverpool-city-region", installed.id)

        val mapFile = File(root, installed.mapDataPath)
        val graphFile = File(root, installed.routingDataPath)
        assertTrue("Regional MBTiles was not installed.", mapFile.isFile && mapFile.length() > 0)
        assertTrue("Valhalla graph was not installed.", graphFile.isFile && graphFile.length() > 0)
        assertTrue(
            "Local Noto Sans glyph resources were not installed.",
            File(root, "map/glyphs/Noto Sans Regular/0-255.pbf").isFile &&
                File(root, "map/glyphs/Noto Sans Regular/256-511.pbf").isFile,
        )

        SQLiteDatabase.openDatabase(mapFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val metadataRows = mutableMapOf<String, String>()
            database.rawQuery("SELECT name, value FROM metadata", null).use { cursor ->
                while (cursor.moveToNext()) metadataRows[cursor.getString(0)] = cursor.getString(1)
            }
            assertEquals("pbf", metadataRows["format"])
            assertTrue(metadataRows["json"].orEmpty().contains("transportation"))
            database.rawQuery("SELECT COUNT(*) FROM tiles", null).use { cursor ->
                assertTrue(cursor.moveToFirst() && cursor.getLong(0) > 0)
            }
        }

        val postcodeHits = transfer.searchInstalledPackages("L1", 50).getOrThrow()
            .filter { !it.result.record.postcode.isNullOrBlank() }
        assertTrue("Liverpool postcode index returned no L1 results.", postcodeHits.isNotEmpty())
        val origin = RouteCoordinate(53.4073, -2.9775)
        val destination = postcodeHits.minBy { hit ->
            val coordinate = hit.result.record.coordinate
            val latDelta = coordinate.latitude - origin.latitude
            val lonDelta = coordinate.longitude - origin.longitude
            latDelta * latDelta + lonDelta * lonDelta
        }.result.record.coordinate

        val style = createRegionalOfflineMapStyle(
            regionalMapDataFileUri(root.absolutePath, installed.mapDataPath),
            installed.attribution ?: "© OpenMapTiles © OpenStreetMap contributors",
        )
        val styleJson = JSONObject(style)
        assertTrue("Offline style must reference the packaged MBTiles file.", style.contains("mbtiles:"))
        assertTrue("Offline style must use packaged glyphs.", style.contains("file:///") && style.contains("/glyphs/"))
        assertTrue(styleJson.getJSONArray("layers").toString().contains("tomos-place-labels"))
        assertTrue(styleJson["layers"].toString().contains("tomos-road-labels"))
        assertFalse("Offline style must not depend on remote tile or font hosts.", style.contains("https://"))

        val runtime = createOfflineValhallaRuntime(root.absolutePath, context)
        val client = ValhallaRoutingClient()
        try {
            val result = runtime.calculateRoute(
                installed.routingDataPath,
                ValhallaRouteRequest(
                    origin = origin,
                    destination = destination,
                    costing = ValhallaCosting.CAR,
                ),
                client,
            )
            assertTrue(
                "The bundled native Valhalla engine failed to route offline: " +
                    (result as? ValhallaRouteResult.Failure)?.reason,
                result is ValhallaRouteResult.Success,
            )
            val success = result as ValhallaRouteResult.Success
            assertTrue(success.geometry.coordinates.size > 2)
            assertTrue(success.route.instructions.isNotEmpty())
            assertTrue(success.route.distanceMeters > 0)
        } finally {
            client.close()
            runtime.stop()
        }

        // Launch the real app against the installed package while airplane mode
        // remains enabled. Its status text changes after the local MBTiles style
        // has been created and selected by the map startup effect.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val offlineMapStatus = device.wait(
                Until.findObject(By.text("Offline map · Liverpool City Region")),
                15_000,
            )
            assertTrue("TomOS Maps did not select the installed offline MBTiles style.", offlineMapStatus != null)
        } finally {
            scenario.close()
            root.deleteRecursively()
        }
    }
}
