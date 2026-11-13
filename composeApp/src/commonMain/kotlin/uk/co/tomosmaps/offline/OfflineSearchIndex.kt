package uk.co.tomosmaps.offline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uk.co.tomosmaps.navigation.RouteCoordinate
import uk.co.tomosmaps.navigation.normalizeUkPostcode

/** One record in the compact, versioned regional offline search index. */
data class OfflineSearchRecord(
    val id: String,
    val label: String,
    val coordinate: RouteCoordinate,
    val postcode: String? = null,
    val category: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(label.isNotBlank())
        require(coordinate.latitude.isFinite() && coordinate.latitude in -90.0..90.0)
        require(coordinate.longitude.isFinite() && coordinate.longitude in -180.0..180.0)
        require(postcode == null || normalizeUkPostcode(postcode) != null)
    }
}

data class OfflineSearchResult(
    val record: OfflineSearchRecord,
    val matchedBy: OfflineSearchMatch,
)

enum class OfflineSearchMatch { POSTCODE, NAME, CATEGORY }

/**
 * Immutable in-memory index loaded from the region package's search-index JSON.
 * Search is deterministic and requires no network connection.
 */
class OfflineSearchIndex private constructor(
    private val records: List<OfflineSearchRecord>,
) {
    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<OfflineSearchResult> {
        require(limit in 1..MAX_LIMIT)
        val input = query.trim()
        if (input.isEmpty()) return emptyList()
        val compactPostcode = input.uppercase().filterNot { it.isWhitespace() }
        val normalizedPostcode = normalizeUkPostcode(input)
        val postcodePrefix = compactPostcode.matches(Regex("[A-Z0-9]{2,7}"))
        val matches = records.mapNotNull { record ->
            val postcode = record.postcode?.uppercase()?.filterNot { it.isWhitespace() }
            val match = when {
                normalizedPostcode != null && postcode == normalizedPostcode -> OfflineSearchMatch.POSTCODE
                postcodePrefix && postcode?.startsWith(compactPostcode) == true -> OfflineSearchMatch.POSTCODE
                record.label.contains(input, ignoreCase = true) -> OfflineSearchMatch.NAME
                record.category?.contains(input, ignoreCase = true) == true -> OfflineSearchMatch.CATEGORY
                else -> null
            }
            match?.let { OfflineSearchResult(record, it) }
        }
        return matches.sortedWith(
            compareBy<OfflineSearchResult> { it.matchedBy.ordinal }
                .thenBy { it.record.label.lowercase() }
                .thenBy { it.record.id }
        ).take(limit)
    }

    fun size(): Int = records.size

    companion object {
        const val FORMAT_VERSION = 1
        const val DEFAULT_LIMIT = 8
        const val MAX_LIMIT = 50

        /** Expected JSON: { "formatVersion": 1, "records": [{...}] }. */
        fun parse(jsonText: String): OfflineSearchIndex {
            require(jsonText.length <= MAX_INDEX_JSON_CHARS) { "Offline search index exceeds the size limit." }
            val root = Json.parseToJsonElement(jsonText).jsonObject
            val version = root["formatVersion"]?.jsonPrimitive?.content?.toIntOrNull()
            require(version == FORMAT_VERSION) { "Unsupported offline search index version." }
            val values = root["records"]?.jsonArray
                ?: throw IllegalArgumentException("Offline search index has no records.")
            require(values.size <= MAX_RECORDS) { "Offline search index contains too many records." }
            val records = values.map { element ->
                val item = element.jsonObject
                val id = item["id"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Search record is missing its id.")
                val label = item["label"]?.jsonPrimitive?.content
                    ?: throw IllegalArgumentException("Search record is missing its label.")
                val latitude = item["latitude"]?.jsonPrimitive?.doubleOrNull
                    ?: throw IllegalArgumentException("Search record has an invalid latitude.")
                val longitude = item["longitude"]?.jsonPrimitive?.doubleOrNull
                    ?: throw IllegalArgumentException("Search record has an invalid longitude.")
                OfflineSearchRecord(
                    id = id,
                    label = label,
                    coordinate = RouteCoordinate(latitude, longitude),
                    postcode = item["postcode"]?.jsonPrimitive?.content,
                    category = item["category"]?.jsonPrimitive?.content,
                )
            }
            require(records.map { it.id }.distinct().size == records.size) {
                "Offline search index contains duplicate record IDs."
            }
            return OfflineSearchIndex(records)
        }

        private const val MAX_RECORDS = 2_000_000
        private const val MAX_INDEX_JSON_CHARS = 256 * 1024 * 1024
    }
}
