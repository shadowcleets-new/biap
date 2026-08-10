package com.sc.biap.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sync key is what stands between the Drive folder and the entire catalogue, so the
 * wrapping around it is tested directly rather than trusted by inspection.
 */
class SyncPassphraseTest {

    private val syncKey = "9r4Xk2mQ7vB1zN5tH8sL0pC3jD6wF2yA4uE7iO1kR9M="

    @Test
    fun `round trips with the correct passphrase`() {
        val envelope = SyncPassphrase.wrap(syncKey, "correct horse battery".toCharArray())
        assertEquals(syncKey, SyncPassphrase.unwrap(envelope, "correct horse battery".toCharArray()))
    }

    @Test
    fun `a wrong passphrase yields null, never garbage`() {
        val envelope = SyncPassphrase.wrap(syncKey, "the right one".toCharArray())
        // GCM authenticates, so this must fail cleanly rather than return plausible bytes
        // that would then be used as an encryption key.
        assertNull(SyncPassphrase.unwrap(envelope, "the wrong one".toCharArray()))
        assertNull(SyncPassphrase.unwrap(envelope, "".toCharArray()))
        assertNull(SyncPassphrase.unwrap(envelope, "the right one ".toCharArray()))
    }

    @Test
    fun `the plaintext key never appears in the envelope`() {
        val envelope = SyncPassphrase.wrap(syncKey, "a passphrase".toCharArray())
        assertFalse(envelope.contains(syncKey))
        assertFalse(envelope.contains(syncKey.substring(0, 12)))
    }

    @Test
    fun `wrapping twice produces different bytes`() {
        val a = SyncPassphrase.wrap(syncKey, "same passphrase".toCharArray())
        val b = SyncPassphrase.wrap(syncKey, "same passphrase".toCharArray())
        // Fresh salt and IV each time, so identical inputs must not be comparable.
        assertNotEquals(a, b)
        assertEquals(syncKey, SyncPassphrase.unwrap(a, "same passphrase".toCharArray()))
        assertEquals(syncKey, SyncPassphrase.unwrap(b, "same passphrase".toCharArray()))
    }

    @Test
    fun `damaged or foreign envelopes are rejected, not guessed at`() {
        val envelope = SyncPassphrase.wrap(syncKey, "pw".toCharArray())
        assertNull(SyncPassphrase.unwrap(envelope.dropLast(4), "pw".toCharArray()))
        assertNull(SyncPassphrase.unwrap("not an envelope", "pw".toCharArray()))
        assertNull(SyncPassphrase.unwrap("", "pw".toCharArray()))
        // A legacy plaintext sync_key.txt must never be mistaken for an envelope.
        assertNull(SyncPassphrase.unwrap(syncKey, "pw".toCharArray()))
    }

    @Test
    fun `a legacy plaintext key is not detected as an envelope`() {
        assertFalse(SyncPassphrase.isEnvelope(syncKey))
        assertFalse(SyncPassphrase.isEnvelope(null))
        assertTrue(SyncPassphrase.isEnvelope(SyncPassphrase.wrap(syncKey, "pw".toCharArray())))
    }

    @Test
    fun `a tampered ciphertext is refused`() {
        val envelope = SyncPassphrase.wrap(syncKey, "pw".toCharArray())
        val parts = envelope.split(":").toMutableList()
        val ct = parts[4]
        parts[4] = (if (ct.first() == 'a') "b" else "a") + ct.drop(1)
        assertNull(SyncPassphrase.unwrap(parts.joinToString(":"), "pw".toCharArray()))
    }
}
