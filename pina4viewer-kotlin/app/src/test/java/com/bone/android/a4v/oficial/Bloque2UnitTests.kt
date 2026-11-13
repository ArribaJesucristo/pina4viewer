package com.bone.android.a4v.oficial

import com.bone.android.a4v.oficial.util.ApkDownloadManager
import com.bone.android.a4v.oficial.util.VpnHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pruebas unitarias de Bloque 2:
 * - Prioridad de VPN (Proton > Psiphon > otras VPNs instaladas)
 * - Verificación criptográfica SHA-256 para descargas OTA e instaladores
 */
class Bloque2UnitTests {

    @Test
    fun `resolvePreferredVpnApp prioritizes Proton over Psiphon when both are installed`() {
        val installed = setOf(VpnHelper.PSIPHON.packageName, VpnHelper.PROTON_VPN.packageName)
        val preferred = VpnHelper.resolvePreferredVpnApp(installed)
        assertEquals(VpnHelper.PROTON_VPN.packageName, preferred?.packageName)
    }

    @Test
    fun `resolvePreferredVpnApp falls back to Psiphon when Proton is absent`() {
        val installed = setOf(VpnHelper.PSIPHON.packageName, "com.wireguard.android")
        val preferred = VpnHelper.resolvePreferredVpnApp(installed)
        assertEquals(VpnHelper.PSIPHON.packageName, preferred?.packageName)
    }

    @Test
    fun `resolvePreferredVpnApp returns secondary installed VPN when neither Proton nor Psiphon exist`() {
        val installed = setOf("com.wireguard.android")
        val preferred = VpnHelper.resolvePreferredVpnApp(installed)
        assertEquals("com.wireguard.android", preferred?.packageName)
    }

    @Test
    fun `resolvePreferredVpnApp returns null when no supported VPN is installed`() {
        assertNull(VpnHelper.resolvePreferredVpnApp(emptySet()))
        assertNull(VpnHelper.resolvePreferredVpnApp(setOf("org.acestream.media")))
    }

    @Test
    fun `computeFileSha256Hex calculates standard NIST SHA-256 digest`() {
        val tempFile = File.createTempFile("pina4viewer_sha_", ".bin")
        try {
            tempFile.writeBytes("abc".toByteArray(Charsets.UTF_8))
            val actual = ApkDownloadManager.computeFileSha256Hex(tempFile)
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", actual)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `matchesExpectedSha256 enforces exact case-insensitive 64-hex verification when provided`() {
        val expectedLower = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val expectedUpper = expectedLower.uppercase()

        assertTrue(ApkDownloadManager.matchesExpectedSha256(expectedLower, expectedUpper))
        assertTrue(ApkDownloadManager.matchesExpectedSha256(expectedLower, "  $expectedUpper  "))
        assertTrue(ApkDownloadManager.matchesExpectedSha256(expectedLower, null))
        assertTrue(ApkDownloadManager.matchesExpectedSha256(expectedLower, "   "))

        assertFalse(ApkDownloadManager.matchesExpectedSha256("0".repeat(64), expectedLower))
        assertFalse(ApkDownloadManager.matchesExpectedSha256(expectedLower, "not-a-valid-sha256"))
    }
}
