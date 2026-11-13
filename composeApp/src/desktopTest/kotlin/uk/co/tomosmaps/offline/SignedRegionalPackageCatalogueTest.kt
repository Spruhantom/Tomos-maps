package uk.co.tomosmaps.offline

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignedRegionalPackageCatalogueTest {
    @Test
    fun acceptsCatalogueOnlyWithPinnedEd25519Signature() = runBlocking {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val document = catalogue()
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(document.encodeToByteArray())
        val signature = Base64.getEncoder().encodeToString(signer.sign())
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        val parsed = SignedRegionalPackageCatalogue.verifyAndParse(document, signature, publicKey)

        assertEquals("gb-liverpool-city-region", parsed.packages.single().id)
        assertEquals("https://packages.example.test/liverpool.zip", parsed.packages.single().archiveUrl)
    }

    @Test
    fun rejectsTamperedCatalogueAndWrongPublisherKey() = runBlocking {
        val first = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val document = catalogue()
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(first.private)
        signer.update(document.encodeToByteArray())
        val signature = Base64.getEncoder().encodeToString(signer.sign())
        val firstKey = Base64.getEncoder().encodeToString(first.public.encoded)
        val otherKey = Base64.getEncoder().encodeToString(other.public.encoded)

        assertFalse(verifyRegionalCatalogueSignature(
            document.replace("Liverpool", "Liverpool!").encodeToByteArray(), signature, firstKey
        ))
        assertFalse(verifyRegionalCatalogueSignature(document.encodeToByteArray(), signature, otherKey))
        assertTrue(verifyRegionalCatalogueSignature(document.encodeToByteArray(), signature, firstKey))
    }

    private fun catalogue() = """{"formatVersion":1,"packages":[{"id":"gb-liverpool-city-region","name":"Liverpool City Region","version":"1.0.0","manifestUrl":"https://packages.example.test/liverpool.json","manifestSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","estimatedSizeBytes":1000000,"archiveUrl":"https://packages.example.test/liverpool.zip","archiveSha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}]}"""
}
