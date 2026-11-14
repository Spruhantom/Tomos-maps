package uk.co.tomosmaps.offline

/**
 * Release-time publisher settings. Keep the private signing key out of the
 * application and repository; only the corresponding public key belongs here.
 * The application deliberately refuses catalogue refresh until configured.
 */
object RegionalPackageBuildConfig {
    const val PINNED_ED25519_PUBLIC_KEY_BASE64 = "MCowBQYDK2VwAyEAW2/sIxbqEzu2OVVURgUYoi6rK2+rzYDtd9H3QJuV1w4="
    const val CATALOGUE_URL = "https://github.com/Spruhantom/Tomos-maps/releases/download/england-regions/catalogue.json"
    const val CATALOGUE_SIGNATURE_URL = "https://github.com/Spruhantom/Tomos-maps/releases/download/england-regions/catalogue.json.sig"

    // Independent custom-area layer feed is enabled only after the publisher
    // creates and signs the matching release assets.
    const val LAYER_CATALOGUE_URL = ""
    const val LAYER_CATALOGUE_SIGNATURE_URL = ""

    val publisherKeyConfigured: Boolean
        get() = PINNED_ED25519_PUBLIC_KEY_BASE64.isNotBlank()
}
