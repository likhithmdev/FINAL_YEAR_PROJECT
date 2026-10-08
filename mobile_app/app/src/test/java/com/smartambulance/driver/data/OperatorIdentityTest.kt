package com.smartambulance.driver.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Operators type a bare ID, Firebase Auth needs an email. This mapping is what
 * replaces the old plaintext-PIN comparison, so getting it wrong locks everyone
 * out — and getting it loose would let an unexpected address be tried.
 */
class OperatorIdentityTest {

    @Test
    fun `the synthetic domain is stable`() {
        // Changing this locks out every existing account, so it is pinned here.
        assertEquals("saptcs.local", SaptcsRepository.OPERATOR_EMAIL_DOMAIN)
    }

    @Test
    fun `a bare operator id becomes a synthetic address`() {
        assertEquals("driver_001@saptcs.local", SaptcsRepository.emailFor("driver_001"))
        assertEquals("police_001@saptcs.local", SaptcsRepository.emailFor("police_001"))
    }

    @Test
    fun `an id is normalised to lower case`() {
        assertEquals("driver_001@saptcs.local", SaptcsRepository.emailFor("Driver_001"))
        assertEquals("admin_001@saptcs.local", SaptcsRepository.emailFor("ADMIN_001"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals("driver_001@saptcs.local", SaptcsRepository.emailFor("  driver_001  "))
    }

    @Test
    fun `a full address is passed through so existing accounts still work`() {
        assertEquals("ops@example.com", SaptcsRepository.emailFor("ops@example.com"))
        assertEquals("driver_001@saptcs.local", SaptcsRepository.emailFor("driver_001@saptcs.local"))
    }

    @Test
    fun `the operator id is whatever precedes the at sign`() {
        assertEquals("driver_001", SaptcsRepository.operatorIdFor("driver_001"))
        assertEquals("driver_001", SaptcsRepository.operatorIdFor("driver_001@saptcs.local"))
        assertEquals("ops", SaptcsRepository.operatorIdFor("ops@example.com"))
        assertEquals("driver_001", SaptcsRepository.operatorIdFor("  driver_001  "))
    }

    @Test
    fun `an empty id maps to an empty local part rather than a valid address`() {
        assertEquals("", SaptcsRepository.operatorIdFor(""))
        assertEquals("@saptcs.local", SaptcsRepository.emailFor(""))
    }

    // -----------------------------------------------------------------------
    // PIN expansion
    //
    // Auth rejects passwords under six characters, so a bare PIN can never be
    // one. These tests pin the prefix contract: get it wrong and every operator
    // is locked out, get it too loose and a real password is mangled.
    // -----------------------------------------------------------------------

    @Test
    fun `the pin prefix is stable`() {
        // The live accounts are stored with exactly this prefix, so changing it
        // locks out every operator until they are re-issued.
        assertEquals("saptcs", SaptcsRepository.PIN_PREFIX)
    }

    @Test
    fun `a four digit pin is expanded to the stored password`() {
        // Deliberately not the live operator PINs: this file is committed and
        // published, and the live PINs are the accounts' only secret. Any four
        // digits exercise exactly the same branch.
        assertEquals("saptcs4321", SaptcsRepository.passwordFor("4321"))
        assertEquals("saptcs8765", SaptcsRepository.passwordFor("8765"))
        assertEquals("saptcs2468", SaptcsRepository.passwordFor("2468"))
        assertEquals("saptcs1357", SaptcsRepository.passwordFor("1357"))
        assertEquals("saptcs9090", SaptcsRepository.passwordFor("9090"))
    }

    @Test
    fun `whitespace around a pin is ignored`() {
        assertEquals("saptcs8765", SaptcsRepository.passwordFor("  8765  "))
    }

    @Test
    fun `a normal password is passed through untouched`() {
        // Accounts created outside the PIN convention must still sign in.
        assertEquals("admin123", SaptcsRepository.passwordFor("admin123"))
        assertEquals("S3cret!Pass", SaptcsRepository.passwordFor("S3cret!Pass"))
        assertEquals("saptcs", SaptcsRepository.passwordFor("saptcs"))
    }

    @Test
    fun `only exactly four digits count as a pin`() {
        // Three and five digits are not treated as PINs, so a numeric password of
        // another length is never silently rewritten.
        assertEquals("123", SaptcsRepository.passwordFor("123"))
        assertEquals("12345", SaptcsRepository.passwordFor("12345"))
        assertEquals("123456", SaptcsRepository.passwordFor("123456"))
    }

    @Test
    fun `a four character non numeric secret is not expanded`() {
        assertEquals("abcd", SaptcsRepository.passwordFor("abcd"))
        assertEquals("12a4", SaptcsRepository.passwordFor("12a4"))
    }

    @Test
    fun `an empty secret stays empty`() {
        assertEquals("", SaptcsRepository.passwordFor(""))
        assertEquals("", SaptcsRepository.passwordFor("   "))
    }
}
