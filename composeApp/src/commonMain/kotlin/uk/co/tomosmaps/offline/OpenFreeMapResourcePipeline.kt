package uk.co.tomosmaps.offline

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider

/**
 * OpenFreeMap request pipeline.
 *
 * All resources from the public OpenFreeMap host are served through one provider
 * so online and offline requests share a strict four-request concurrency limit.
 * Offline-pack requests additionally consume a per-download tile and byte quota.
 */
object OpenFreeMapResourcePipeline {
    const val USER_AGENT = "TomOSMaps/0.2 (+https://github.com/Spruhantom/Tomos-maps)"
    const val MAX_PARALLEL_REQUESTS = 4
    const val MAX_TILES_PER_DOWNLOAD = 3_000
    const val MAX_BYTES_PER_DOWNLOAD = 50L * 1024L * 1024L

    private val requestSlots = Semaphore(MAX_PARALLEL_REQUESTS)
    private val httpClient = HttpClient(CIO)
    val quota = OpenFreeMapOfflineQuota()

    val provider = MapResourceProvider(
        accepts = { request ->
            request.url.startsWith("https://tiles.openfreemap.org/")
        },
        load = { request ->
            requestSlots.withPermit {
                try {
                    val offline = request.usage == MapResourceLoadRequest.Usage.Offline
                    if (offline) {
                        quota.reserveTileRequest(request.kind == MapResourceKind.Tile)
                    }

                    val response = httpClient.get(request.url) {
                        header(HttpHeaders.UserAgent, USER_AGENT)
                        request.priorEtag?.let { header(HttpHeaders.IfNoneMatch, it) }
                        request.range?.let {
                            header(HttpHeaders.Range, "bytes=${it.first}-${it.last}")
                        }
                    }

                    if (response.status.value == 304) {
                        return@withPermit MapResourceLoad.NotModified()
                    }
                    if (response.status.value !in 200..299) {
                        val reason = when (response.status.value) {
                            404 -> MapResourceError.NotFound
                            429 -> MapResourceError.RateLimit
                            in 500..599 -> MapResourceError.Server
                            else -> MapResourceError.Other
                        }
                        return@withPermit MapResourceLoad.Failed(
                            reason,
                            "OpenFreeMap returned HTTP ${response.status.value}.",
                        )
                    }

                    val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                    if (offline && declaredLength != null) {
                        quota.reserveBytes(declaredLength)
                    }

                    val bytes = response.bodyAsBytes()
                    if (offline && declaredLength == null) {
                        quota.reserveBytes(bytes.size.toLong())
                    }
                    MapResourceLoad.Bytes(
                        bytes = bytes,
                        etag = response.headers[HttpHeaders.ETag],
                    )
                } catch (exception: Exception) {
                    MapResourceLoad.Failed(
                        MapResourceError.Other,
                        exception.message?.takeIf { it.isNotBlank() }
                            ?: "Unable to load an OpenFreeMap resource.",
                    )
                }
            }
        },
    )
}

/**
 * Tracks one offline pack at a time. Reservations happen before requests are
 * sent (tile count) or before known-length bodies are read (byte count).
 * Unknown-length responses are measured after download and rejected if over
 * the limit. A failed quota check stops new requests instead of continuing.
 */
class OpenFreeMapOfflineQuota {
    private val mutex = Mutex()
    private var activeDownloadId: String? = null
    private var requestedTiles = 0
    private var reservedBytes = 0L

    suspend fun begin(downloadId: String) = mutex.withLock {
        require(downloadId.isNotBlank()) { "Offline download ID must not be blank." }
        check(activeDownloadId == null) { "Another OpenFreeMap offline download is already active." }
        activeDownloadId = downloadId
        requestedTiles = 0
        reservedBytes = 0L
    }

    suspend fun reserveTileRequest(isTile: Boolean) = mutex.withLock {
        check(activeDownloadId != null) {
            "OpenFreeMap offline requests must be started by the TomOS offline map manager."
        }
        if (isTile) {
            check(requestedTiles < OpenFreeMapResourcePipeline.MAX_TILES_PER_DOWNLOAD) {
                "OpenFreeMap offline download stopped at the 3,000-tile limit."
            }
            requestedTiles += 1
        }
    }

    suspend fun reserveBytes(sizeBytes: Long) = mutex.withLock {
        require(sizeBytes >= 0L)
        check(activeDownloadId != null) {
            "OpenFreeMap offline download is no longer active."
        }
        check(sizeBytes <= OpenFreeMapResourcePipeline.MAX_BYTES_PER_DOWNLOAD - reservedBytes) {
            "OpenFreeMap offline download stopped at the 50 MB limit."
        }
        reservedBytes += sizeBytes
    }

    suspend fun finish(downloadId: String) = mutex.withLock {
        if (activeDownloadId == downloadId) {
            activeDownloadId = null
            requestedTiles = 0
            reservedBytes = 0L
        }
    }

    suspend fun currentUsage(): Pair<Int, Long> = mutex.withLock {
        requestedTiles to reservedBytes
    }
}
