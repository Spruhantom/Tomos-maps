package uk.co.tomosmaps.offline

import java.io.File

actual fun regionalMapDataFileUri(storageDirectory: String, relativePath: String): String {
    require(isSafePackagePath(relativePath)) { "Regional map path is unsafe." }
    val root = File(storageDirectory).canonicalFile
    val file = File(root, relativePath).canonicalFile
    require(file.toPath().startsWith(root.toPath()) && file != root) {
        "Regional map path escapes application storage."
    }
    require(file.isFile && file.length() > 0L) { "Installed regional MBTiles file is missing or empty." }
    return file.toURI().toString()
}
