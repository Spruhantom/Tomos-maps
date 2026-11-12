package uk.co.tomosmaps.offline

/**
 * Versioned metadata stored inside MapLibre offline packs.
 *
 * Keep this parser strict: malformed or foreign packs must never be mistaken
 * for a TomOS region, or accidentally selected for pause/resume/delete.
 */
internal data class OfflineRegionMetadata(
    val id: String,
    val name: String,
    val providerId: String,
) {
    fun encode(): ByteArray = listOf(VERSION, id, name, providerId)
        .joinToString("\n")
        .encodeToByteArray()

    companion object {
        private const val VERSION = "tomos-offline-region-v1"

        fun decode(bytes: ByteArray?): OfflineRegionMetadata? {
            val fields = bytes?.decodeToString()?.split('\n') ?: return null
            if (fields.size != 4 || fields[0] != VERSION) return null
            if (fields.drop(1).any { it.isBlank() || '\r' in it }) return null
            return OfflineRegionMetadata(
                id = fields[1],
                name = fields[2],
                providerId = fields[3],
            )
        }
    }
}
