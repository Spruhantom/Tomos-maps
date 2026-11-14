package uk.co.tomosmaps.offline

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider

actual suspend fun verifyRegionalCatalogueSignature(
    payload: ByteArray,
    signatureBase64: String,
    pinnedEd25519PublicKeyBase64: String,
): Boolean = runCatching {
    val keyBytes = Base64.getDecoder().decode(pinnedEd25519PublicKeyBase64)
    val signatureBytes = Base64.getDecoder().decode(signatureBase64)
    val provider = BouncyCastleProvider()
    val publicKey = KeyFactory.getInstance("Ed25519", provider)
        .generatePublic(X509EncodedKeySpec(keyBytes))
    Signature.getInstance("Ed25519", provider).run {
        initVerify(publicKey)
        update(payload)
        verify(signatureBytes)
    }
}.getOrDefault(false)
