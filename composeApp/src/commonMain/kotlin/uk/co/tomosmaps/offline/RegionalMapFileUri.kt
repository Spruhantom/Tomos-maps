package uk.co.tomosmaps.offline

/** Resolve a verified package-relative map path to a canonical local file URI. */
expect fun regionalMapDataFileUri(storageDirectory: String, relativePath: String): String
