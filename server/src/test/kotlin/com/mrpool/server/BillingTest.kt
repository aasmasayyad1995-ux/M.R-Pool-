package com.mrpool.server

import com.mrpool.server.billing.Billing
import com.mrpool.server.billing.BillingConfig
import com.mrpool.server.billing.ClaimState
import com.mrpool.server.billing.Entitlements
import com.mrpool.server.billing.Signature
import com.mrpool.server.billing.Throttle
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The subscription: a UPI link, a reference, and a person who checks the bank.
 *
 * Somebody's money is on the other end, so these tests spend most of their time on the two
 * failures that cost something: handing a subscription to a player who has not paid, and
 * losing one from a player who has.
 */
class BillingTest {

    private val token = "admin-password-long-enough"
    private val day = Entitlements.DAY_MILLIS
    private val temporaryFiles = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = temporaryFiles.forEach { it.delete() }

    private fun temporaryFile(): File =
        Files.createTempFile("entitlements", ".jsonl").toFile()
            .also { it.delete(); temporaryFiles.add(it) }

    private fun config(storePath: String = "/tmp/never-written.jsonl") = BillingConfig(
        upiId = "asad@okhdfcbank",
        payeeName = "Mr. Pool",
        amount = "99",
        adminToken = token,
        days = 30,
        storePath = storePath
    )

    private var now = 1_700_000_000_000L

    private fun billing(
        file: File? = null,
        throttle: Throttle = Throttle(limit = 8, windowMillis = 60L * 60L * 1000L)
    ) = Billing(
        config = config(),
        entitlements = Entitlements(file),
        clock = { now },
        random = Random(7),
        throttle = throttle
    )

    // ----------------------------------------------------------------- not set up yet

    @Test
    fun `a server with no UPI id sells nothing and says so`() {
        val bare = Billing(config = BillingConfig(upiId = "", amount = "", adminToken = ""))
        assertFalse(bare.isConfigured)
        assertNull(bare.paymentInstructions("p1", "1.2.3.4"))
        assertFalse(bare.statusFor("p1").active)
    }

    @Test
    fun `a server with nowhere to remember subscribers refuses to sell`() {
        val noDisk = Billing(
            config = BillingConfig(
                upiId = "a@b", amount = "99", adminToken = "long-enough-token", storePath = ""
            )
        )
        assertFalse(
            noDisk.isConfigured,
            "taking money and forgetting who paid is worse than not taking it"
        )
        assertNull(noDisk.paymentInstructions("p1", "1.2.3.4"))
    }

    @Test
    fun `an admin page with no password set is switched off, not left open`() {
        val noToken = Billing(
            config = BillingConfig(
                upiId = "a@b", amount = "99", adminToken = "", storePath = "/tmp/x.jsonl"
            )
        )
        assertFalse(
            noToken.isConfigured,
            "without a password the approvals page would be open to anyone who found it"
        )
        assertFalse(noToken.isOwner(""))
        assertFalse(noToken.isOwner("anything"))
    }

    // ------------------------------------------------------------------- paying

    @Test
    fun `the payment link carries the UPI id, the amount and the reference`() {
        val instructions = assertNotNull(billing().paymentInstructions("p1", "1.2.3.4"))
        assertTrue(instructions.payLink.startsWith("upi://pay?"))
        assertTrue(instructions.payLink.contains("asad%40okhdfcbank"), "the owner's UPI id is missing")
        assertTrue(instructions.payLink.contains("am=99"), "the amount is missing")
        assertTrue(instructions.payLink.contains("cu=INR"), "the currency is missing")
        assertTrue(
            instructions.payLink.contains(instructions.reference),
            "the reference must reach the bank statement, or the payment cannot be matched"
        )
    }

    @Test
    fun `asking twice gives the same reference, not two rows to check`() {
        val service = billing()
        val first = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))
        val second = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))
        assertEquals(first.reference, second.reference)
        assertEquals(0, service.queue().size, "nothing is waiting until the player says so")
    }

    @Test
    fun `saying you have paid grants nothing by itself`() {
        val service = billing()
        val instructions = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))

        assertTrue(service.submitClaim("p1", instructions.reference, "402312345678"))
        assertFalse(
            service.statusFor("p1").active,
            "a player's word is not a payment — only the owner's approval is"
        )
        assertEquals(ClaimState.SUBMITTED, service.statusFor("p1").claimState)
        assertEquals(1, service.queue().size)
    }

    @Test
    fun `a reference cannot be claimed by the player it was not minted for`() {
        val service = billing()
        val mine = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))

        assertFalse(
            service.submitClaim("p2", mine.reference, "402312345678"),
            "a reference travels through a UPI note and can be seen"
        )
        assertFalse(service.submitClaim("p2", "MRP-ZZZZZ", "1"), "and one that does not exist")
        assertTrue(service.queue().isEmpty())
    }

    @Test
    fun `an enormous transaction number is cut down rather than stored whole`() {
        val service = billing()
        val instructions = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))
        service.submitClaim("p1", instructions.reference, "9".repeat(5000))
        val claim = assertNotNull(service.queue().firstOrNull())
        assertTrue(claim.utr.length <= Billing.MAX_UTR)
    }

    // -------------------------------------------------------------- the owner decides

    @Test
    fun `approving a payment turns the subscription on for a month`() {
        val service = billing()
        val instructions = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))
        service.submitClaim("p1", instructions.reference, "402312345678")

        assertNotNull(service.approve(instructions.reference, "seen in the bank"))

        val status = service.statusFor("p1")
        assertTrue(status.active)
        assertEquals(now + 30 * day, status.activeUntilMillis)
        assertEquals(ClaimState.APPROVED, status.claimState)
        assertTrue(service.queue().isEmpty(), "an approved payment leaves the queue")
    }

    @Test
    fun `rejecting a payment leaves the player exactly where they were`() {
        val service = billing()
        val instructions = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))
        service.submitClaim("p1", instructions.reference, "nonsense")

        assertTrue(service.reject(instructions.reference, "no such payment"))
        assertFalse(service.statusFor("p1").active)
        assertEquals(ClaimState.REJECTED, service.statusFor("p1").claimState)
        assertTrue(service.queue().isEmpty())
    }

    @Test
    fun `paying again before the month is up adds to it rather than replacing it`() {
        val service = billing()
        val first = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))
        service.submitClaim("p1", first.reference, "1")
        service.approve(first.reference)

        now += 10 * day
        val second = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))
        service.submitClaim("p1", second.reference, "2")
        service.approve(second.reference)

        assertEquals(
            1_700_000_000_000L + 60 * day,
            service.statusFor("p1").activeUntilMillis,
            "the twenty days still owed must not be thrown away"
        )
    }

    @Test
    fun `a subscription ends when the month it paid for ends`() {
        val service = billing()
        val instructions = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))
        service.submitClaim("p1", instructions.reference, "1")
        service.approve(instructions.reference)

        now += 29 * day
        assertTrue(service.statusFor("p1").active, "still inside the month")
        now += 2 * day
        assertFalse(service.statusFor("p1").active, "and over once the month is")
    }

    @Test
    fun `the owner can take back a subscription that turned out not to be paid for`() {
        val service = billing()
        val instructions = assertNotNull(service.paymentInstructions("p1", "1.2.3.4"))
        service.submitClaim("p1", instructions.reference, "1")
        service.approve(instructions.reference)
        assertTrue(service.statusFor("p1").active)

        service.revoke("p1")
        assertFalse(service.statusFor("p1").active)
    }

    @Test
    fun `approving something that was never claimed does nothing`() {
        val service = billing()
        assertNull(service.approve("MRP-NOPE"))
        assertFalse(service.reject("MRP-NOPE"))
    }

    // ----------------------------------------------------------------- the password

    @Test
    fun `only the admin password opens the approvals page`() {
        val service = billing()
        assertTrue(service.isOwner(token))
        assertFalse(service.isOwner(null))
        assertFalse(service.isOwner(""))
        assertFalse(service.isOwner("wrong"))
        assertFalse(service.isOwner(token + "x"))
        assertFalse(service.isOwner(token.dropLast(1)))
        assertFalse(
            service.isOwner(token.dropLast(1) + "X"),
            "right up to the last character is still wrong"
        )
    }

    @Test
    fun `comparing a secret does not stop at the first wrong character`() {
        assertTrue(Signature.constantTimeEquals("abcdef", "abcdef"))
        assertFalse(Signature.constantTimeEquals("abcdef", "abcdeX"))
        assertFalse(Signature.constantTimeEquals("abcdef", "Xbcdef"))
        assertFalse(Signature.constantTimeEquals("abcdef", "abcde"))
        assertFalse(Signature.constantTimeEquals("", "abcdef"))
        assertFalse(Signature.constantTimeEquals("abcdef", null))
    }

    // ---------------------------------------------------------------- staying alive

    @Test
    fun `a subscription survives the server being restarted`() {
        val file = temporaryFile()
        val first = billing(file)
        val instructions = assertNotNull(first.paymentInstructions("p1", "1.2.3.4"))
        first.submitClaim("p1", instructions.reference, "402312345678")
        first.approve(instructions.reference)

        val afterRestart = billing(file)
        assertTrue(
            afterRestart.statusFor("p1").active,
            "a redeploy must not make the player pay again"
        )
    }

    @Test
    fun `a payment waiting to be checked survives a restart too`() {
        val file = temporaryFile()
        val first = billing(file)
        val instructions = assertNotNull(first.paymentInstructions("p1", "1.2.3.4"))
        first.submitClaim("p1", instructions.reference, "402312345678")

        val afterRestart = billing(file)
        val waiting = afterRestart.queue()
        assertEquals(1, waiting.size, "a restart must not lose somebody's money")
        assertEquals(instructions.reference, waiting.first().reference)
        assertEquals("402312345678", waiting.first().utr)
    }

    // ---------------------------------------------------------------------- flooding

    @Test
    fun `one address cannot fill the owner's queue`() {
        val service = billing(throttle = Throttle(limit = 8, windowMillis = 3_600_000L))
        var allowed = 0
        repeat(50) { if (service.paymentInstructions("player-$it", "9.9.9.9") != null) allowed++ }
        assertEquals(8, allowed, "the throttle should have stopped the rest")
    }

    @Test
    fun `the throttle lets the same caller back after the window passes`() {
        var clock = 1_000_000L
        val throttle = Throttle(limit = 2, windowMillis = 1000L)
        assertTrue(throttle.allow("a", clock))
        assertTrue(throttle.allow("a", clock))
        assertFalse(throttle.allow("a", clock))
        clock += 1001L
        assertTrue(throttle.allow("a", clock))
    }

    @Test
    fun `an absurd player id is refused`() {
        val service = billing()
        assertNull(service.paymentInstructions("", "1.2.3.4"))
        assertNull(service.paymentInstructions("x".repeat(500), "1.2.3.4"))
    }
}
