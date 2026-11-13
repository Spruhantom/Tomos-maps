package uk.co.tomosmaps.navigation

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.TimeUnit

actual fun createOfflineValhallaRuntime(storageDirectory: String): OfflineValhallaRuntime =
    AndroidOfflineValhallaRuntime(storageDirectory)

private class AndroidOfflineValhallaRuntime(storageDirectory: String) : OfflineValhallaRuntime {
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
            val abi = Build.SUPPORTED_ABIS.firstOrNull()
                ?: error("Android did not report a supported CPU ABI for the Valhalla engine.")
            val executable = File(root, "engines/$abi/valhalla_service")
            require(executable.isFile) {
                "The offline routing graph is installed, but the Valhalla engine for $abi is not bundled. Add the matching valhalla_service binary under engines/$abi/."
            }
            executable.setExecutable(true, true)
            require(executable.canExecute()) { "The bundled Valhalla engine is not executable." }
            val runtimeDir = File(root, "valhalla-runtime").apply { mkdirs() }
            val port = ServerSocket(0).use { it.localPort }
            val config = File(runtimeDir, "valhalla-$port.json")
            val escaped = graph.parentFile.canonicalPath.replace("\\", "\\\\").replace("\"", "\\\"")
            config.writeText("""{"mjolnir":{"tile_dir":"$escaped"},"httpd":{"service":{"listen":"tcp://127.0.0.1:$port"}}}""")
            val log = File(runtimeDir, "valhalla-$port.log")
            val started = ProcessBuilder(executable.absolutePath, config.absolutePath, "1")
                .redirectErrorStream(true)
                .redirectOutput(log)
                .start()
            process = started
            val endpoint = "http://127.0.0.1:$port"
            val ready = awaitReady(started, port)
            if (!ready) {
                stopProcess()
                val detail = log.takeIf { it.isFile }?.readText()?.takeLast(1000).orEmpty()
                error("Bundled Android Valhalla engine did not start. $detail")
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
}
