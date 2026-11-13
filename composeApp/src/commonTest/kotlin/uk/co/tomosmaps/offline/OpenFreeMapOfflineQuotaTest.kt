package uk.co.tomosmaps.offline

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenFreeMapOfflineQuotaTest {
    @Test
    fun enforcesThreeThousandTileLimitPerDownload() = runBlocking {
        val quota = OpenFreeMapOfflineQuota()
        val generation = quota.begin("liverpool-centre")

        repeat(OpenFreeMapResourcePipeline.MAX_TILES_PER_DOWNLOAD) {
            quota.reserveTileRequest(isTile = true)
        }
        assertFailsWith<IllegalStateException> {
            quota.reserveTileRequest(isTile = true)
        }
        assertEquals(OpenFreeMapResourcePipeline.MAX_TILES_PER_DOWNLOAD, quota.currentUsage().first)

        quota.finish("liverpool-centre", generation)
        assertEquals(0, quota.currentUsage().first)
    }

    @Test
    fun enforcesFiftyMegabyteLimit() = runBlocking {
        val quota = OpenFreeMapOfflineQuota()
        val generation = quota.begin("small-area")
        quota.reserveBytes(OpenFreeMapResourcePipeline.MAX_BYTES_PER_DOWNLOAD)

        assertFailsWith<IllegalStateException> {
            quota.reserveBytes(1L)
        }
        assertEquals(OpenFreeMapResourcePipeline.MAX_BYTES_PER_DOWNLOAD, quota.currentUsage().second)

        quota.finish("small-area", generation)
        assertEquals(0L, quota.currentUsage().second)
    }

    @Test
    fun onlyOneOfflinePackCanOwnTheQuotaAtATime() = runBlocking {
        val quota = OpenFreeMapOfflineQuota()
        val generation = quota.begin("area-a")

        assertFailsWith<IllegalStateException> {
            quota.begin("area-b")
        }
        quota.finish("area-a", generation)
        val nextGeneration = quota.begin("area-b")
        assertTrue(nextGeneration > generation)
        quota.finish("area-b", nextGeneration)
    }

    @Test
    fun staleCompletionCannotReleaseANewerDownload() = runBlocking {
        val quota = OpenFreeMapOfflineQuota()
        val oldGeneration = quota.begin("same-area")
        quota.finish("same-area", oldGeneration)
        val newGeneration = quota.begin("same-area")

        quota.finish("same-area", oldGeneration)

        quota.reserveTileRequest(isTile = true)
        assertEquals(1, quota.currentUsage().first)
        quota.finish("same-area", newGeneration)
    }

    @Test
    fun rejectsOfflineResourceRequestsWithoutAnActiveManagerDownload() = runBlocking {
        val quota = OpenFreeMapOfflineQuota()
        assertFailsWith<IllegalStateException> {
            quota.reserveTileRequest(isTile = true)
        }
        assertEquals(0, quota.currentUsage().first)
    }
}
