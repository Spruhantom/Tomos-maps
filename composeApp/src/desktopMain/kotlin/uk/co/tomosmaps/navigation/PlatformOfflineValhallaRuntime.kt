package uk.co.tomosmaps.navigation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.TimeUnit

actual fun createOfflineValhallaRuntime(storageDirectory: String): OfflineValhallaRuntime =
    DesktopOfflineValhallaRuntime(storageDirectory)

private class DesktopOfflineValhallaRuntime(storageDirectory: String) : OfflineValhallaRuntime {
    private val root = File(storageDirectory).canonicalFile
    private var process: Process? = null
    private var activeGraph: String? = null
    private var activeEndpoint: String? = null

    override suspend fun start(routingGraphPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val graph = File(root, routingGraphPath).canonicalFile
            require(graph.toPath().startsWith(root.toPath()) && graph != root) {
                "Regional routing graph path escapes application storage."
            }
            require(graph.isFile && graph.length() > 0L) { "The installed Valhalla routing graph is missing." }
            if (process?.isAlive == true && activeGraph == graph.path && activeEndpoint != null) {
                return@runCatching activeEndpoint!!
            }
            stopProcess()
            val executable = System.getenv("TOMOS_VALHALLA_SERVICE")?.takeIf { it.isNotBlank() }
                ?: "valhalla_service"
            val runtimeDir = File(root, "valhalla-runtime").apply { mkdirs() }
            val port = ServerSocket(0).use { it.localPort }
            val config = File(runtimeDir, "valhalla-$port.json")
            config.writeText(valhallaConfig(graph, port), Charsets.UTF_8)
            val log = File(runtimeDir, "valhalla-$port.log")
            val started = ProcessBuilder(executable, config.absolutePath, "1")
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
            process = started
            val endpoint = "http://127.0.0.1:$port"
            val ready = awaitReady(started, port)
            if (!ready) {
                stopProcess()
                val detail = log.takeIf { it.isFile }?.readText()?.takeLast(1200).orEmpty()
                error("Local Valhalla did not start. Install valhalla_service and check its log. $detail")
            }
            activeGraph = graph.path
            activeEndpoint = endpoint
            endpoint
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) { stopProcess() }

    private fun awaitReady(started: Process, port: Int): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline && started.isAlive) {
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 300) }
                return true
            } catch (_: Exception) {
                Thread.sleep(150)
            }
        }
        return false
    }

    private fun stopProcess() {
        process?.let { running ->
            running.destroy()
            if (!running.waitFor(3, TimeUnit.SECONDS)) running.destroyForcibly()
        }
        process = null
        activeGraph = null
        activeEndpoint = null
    }

    private fun valhallaConfig(graphArchive: File, port: Int): String {
        val tileDirectory = jsonEscape(graphArchive.parentFile.canonicalPath)
        val tileExtract = jsonEscape(graphArchive.canonicalPath)
        return "{\"mjolnir\":{\"tile_dir\":\"" + tileDirectory +
            "\",\"tile_extract\":\"" + tileExtract +
            "\"},\"httpd\":{\"service\":{\"listen\":\"tcp://127.0.0.1:" + port + "\"}}}"
    }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
