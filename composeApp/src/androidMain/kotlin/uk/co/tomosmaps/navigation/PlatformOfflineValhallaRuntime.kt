package uk.co.tomosmaps.navigation

import android.content.Context
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual fun createOfflineValhallaRuntime(
    storageDirectory: String,
    platformContext: Any?,
): OfflineValhallaRuntime {
    val context = platformContext as? Context
        ?: error("Android Valhalla requires an application Context.")
    return AndroidOfflineValhallaRuntime(context.applicationContext, storageDirectory)
}

/**
 * Uses the upstream Valhalla Mobile JNI library. Its prebuilt native libraries
 * are packaged for Android ABIs by the Maven dependency; no shell service or
 * executable copied from app assets is required.
 */
private class AndroidOfflineValhallaRuntime(
    private val context: Context,
    storageDirectory: String,
) : OfflineValhallaRuntime {
    private val root = File(storageDirectory).canonicalFile
    private var engine: Valhalla? = null
    private var activeGraph: String? = null

    override suspend fun start(routingGraphPath: String): Result<String> =
        Result.failure(UnsupportedOperationException(
            "Android Valhalla runs in-process through JNI and does not expose an HTTP endpoint."
        ))

    override suspend fun calculateRoute(
        routingGraphPath: String,
        request: ValhallaRouteRequest,
        routingClient: ValhallaRoutingClient,
    ): ValhallaRouteResult = withContext(Dispatchers.IO) {
        runCatching {
            val graph = File(root, routingGraphPath).canonicalFile
            require(graph.toPath().startsWith(root.toPath()) && graph != root) {
                "Regional routing graph path escapes application storage."
            }
            require(graph.isFile && graph.length() > 0L) {
                "The installed Valhalla routing tile archive is missing."
            }
            val routingDirectory = requireNotNull(graph.parentFile)
            val adminDatabase = File(routingDirectory, "admins.sqlite")
            val timezoneDatabase = File(routingDirectory, "timezones.sqlite")
            val configFile = File(routingDirectory, "valhalla.json")
            require(adminDatabase.isFile && adminDatabase.length() > 0L) {
                "The installed Valhalla administrative database is missing."
            }
            require(timezoneDatabase.isFile && timezoneDatabase.length() > 0L) {
                "The installed Valhalla timezone database is missing."
            }
            require(configFile.isFile && configFile.length() > 0L) {
                "The installed Valhalla configuration is missing."
            }
            if (engine == null || activeGraph != graph.path) {
                engine?.close()
                engine = null
                activeGraph = null
                val defaults = ValhallaConfigFactory.fromFile(configFile)
                val mjolnir = requireNotNull(defaults.mjolnir) {
                    "The installed Valhalla configuration has no Mjolnir settings."
                }
                val config = defaults.copy(
                    mjolnir = mjolnir.copy(
                        tileDir = routingDirectory.absolutePath,
                        tileExtract = graph.absolutePath,
                        admin = adminDatabase.absolutePath,
                        timezone = timezoneDatabase.absolutePath,
                    )
                )
                val created = Valhalla(context, config)
                engine = created
                activeGraph = graph.path
            }
            val rawResponse = requireNotNull(engine).routeRaw(
                ValhallaRouteRequestBuilder.jsonBody(request)
            )
            routingClient.decodeRouteResponse(rawResponse)
        }.getOrElse { failure ->
            ValhallaRouteResult.Failure(
                failure.message?.takeIf(String::isNotBlank)
                    ?: "The on-device Valhalla engine could not calculate this route."
            )
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        engine?.close()
        engine = null
        activeGraph = null
    }
}
