package uk.co.tomosmaps

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class LiverpoolOfflineNavigationTest {
    @Test
    fun rendersInstalledMbtilesAndRoutesWithNetworkDisabled() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            "The end-to-end test must run with airplane mode enabled.",
            1,
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0),
        )

        val root = File(context.cacheDir, "liverpool-offline-e2e").apply {
            deleteRecursively()
            mkdirs()
        }
        val packageZip = File(root, "liverpool-region.zip")
        context.assets.open("liverpool-region.zip").use { input ->
            packageZip.outputStream().use { output -> input.copyTo(output) }
        }

        ZipInputStream(packageZip.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val relative = entry.name
                require(!relative.startsWith("/") && !relative.split('/').any { it == ".." }) {
                    "Regional test package contains an unsafe path."
                }
                if (!entry.isDirectory &&
                    (relative in setOf("map/region.mbtiles", "routing/tiles.tar") ||
                        relative.startsWith("map/glyphs/"))
                ) {
                    val output = File(root, relative)
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { sink -> zip.copyTo(sink) }
                }
                zip.closeEntry()
            }
        }

        val mbtiles = File(root, "map/region.mbtiles")
        val graph = File(root, "routing/tiles.tar")
        assertTrue("Regional MBTiles was not extracted.", mbtiles.isFile && mbtiles.length() > 0)
        assertTrue("Valhalla graph was not extracted.", graph.isFile && graph.length() > 0)
        assertTrue(
            "Local Noto Sans glyph resources were not extracted.",
            File(root, "map/glyphs/Noto Sans Regular/0-255.pbf").isFile &&
                File(root, "map/glyphs/Noto Sans Regular/256-511.pbf").isFile,
        )

        SQLiteDatabase.openDatabase(mbtiles.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            val metadata = mutableMapOf<String, String>()
            database.rawQuery("SELECT name, value FROM metadata", null).use { cursor ->
                while (cursor.moveToNext()) metadata[cursor.getString(0)] = cursor.getString(1)
            }
            assertEquals("pbf", metadata["format"])
            assertTrue(metadata["json"].orEmpty().contains("transportation"))
            database.rawQuery("SELECT COUNT(*) FROM tiles", null).use { cursor ->
                assertTrue(cursor.moveToFirst() && cursor.getLong(0) > 0)
            }
        }

        val style = createRegionalOfflineMapStyle(
            mbtiles.toURI().toString(),
            "© OpenMapTiles © OpenStreetMap contributors; Contains OS data © Crown copyright and database right",
        )
        val styleJson = Json.parseToJsonElement(style).jsonObject
        assertTrue("Offline style must reference the packaged MBTiles file.", style.contains("mbtiles:"))
        assertTrue("Offline style must use packaged glyphs.", style.contains("file:///") && style.contains("/glyphs/"))
        assertTrue(styleJson["layers"].toString().contains("tomos-place-labels"))
        assertTrue(styleJson["layers"].toString().contains("tomos-road-labels"))
        assertFalse("Offline style must not depend on remote tile or font hosts.", style.contains("https://"))

        val runtime = createOfflineValhallaRuntime(root.absolutePath, context)
        val client = ValhallaRoutingClient()
        try {
            val result = runtime.calculateRoute(
                "routing/tiles.tar",
                ValhallaRouteRequest(
                    origin = RouteCoordinate(53.4073, -2.9775),
                    destination = RouteCoordinate(53.4016, -2.9915),
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
            root.deleteRecursively()
        }
    }
}
