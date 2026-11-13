package uk.co.tomosmaps.offline

/**
 * Release-time publisher settings. Keep the private signing key out of the
 * application and repository; only the corresponding public key belongs here.
 * The application deliberately refuses catalogue refresh until configured.
 */
object RegionalPackageBuildConfig {
    const val PINNED_ED25519_PUBLIC_KEY_BASE64 = ""
    const val CATALOGUE_URL = ""
    const val CATALOGUE_SIGNATURE_URL = ""

    val publisherKeyConfigured: Boolean
        get() = PINNED_ED25519_PUBLIC_KEY_BASE64.isNotBlank()
}
