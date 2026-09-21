package com.mrpool.server

import com.mrpool.server.billing.Billing
import com.mrpool.server.billing.BillingConfig
import com.mrpool.server.billing.Entitlement
import com.mrpool.server.billing.Entitlements
import com.mrpool.server.billing.NewSubscription
import com.mrpool.server.billing.Signature
import com.mrpool.server.billing.SubscriptionGateway
import com.mrpool.server.billing.Throttle
import com.mrpool.server.billing.Webhook
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The subscription, from the signature upwards.
 *
 * Real money hangs off this code, so the tests spend most of their time on the two ways it
 * could go wrong that cost somebody something: granting a subscription nobody paid for,
 * and losing one somebody did.
 */
class BillingTest {

    private val secret = "whsec_test_9f2c"
    private val temporaryFiles = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        temporaryFiles.forEach { it.delete() }
    }

    private fun temporaryFile(): File =
        Files.createTempFile("entitlements", ".jsonl").toFile()
            .also { it.delete(); temporaryFiles.add(it) }

    /** A webhook body shaped like the ones Razorpay actually sends. */
    private fun webhookBody(
        event: String = "subscription.charged",
        subscriptionId: String = "sub_test_1",
        status: String = "active",
        currentEndSeconds: Long = 1_700_000_000L,
        playerNote: String? = "player-a"
    ): String {
        val notes = if (playerNote == null) "{}" else """{"player_id":"$playerNote"}"""
        return """
            {"entity":"event","account_id":"acc_1","event":"$event",
             "contains":["subscription"],
             "payload":{"subscription":{"entity":{
               "id":"$subscriptionId","entity":"subscription","plan_id":"plan_1",
               "status":"$status","current_start":1697408000,
               "current_end":$currentEndSeconds,"notes":$notes}}},
             "created_at":1697408100}
        """.trimIndent().replace("\n", "")
    }

    private fun sign(body: String): String = Signature.hmacSha256(body, secret)

    /** A gateway that takes no money and remembers what it was asked for. */
    private class FakeGateway(
        var failNext: Boolean = false
    ) : SubscriptionGateway {
        val created = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        override fun createSubscription(playerId: String): NewSubscription? {
            if (failNext) return null
            created.add(playerId)
            return NewSubscription("sub_${created.size}", "https://rzp.io/i/fake${created.size}")
        }

        override fun cancelSubscription(subscriptionId: String): Boolean {
            cancelled.add(subscriptionId)
            return true
        }
    }

    private fun billing(
        gateway: SubscriptionGateway? = FakeGateway(),
        file: File? = null,
        now: () -> Long = { 1_600_000_000_000L }
    ): Billing = Billing(
        config = BillingConfig(
            keyId = "rzp_test_key",
            keySecret = "secret",
            webhookSecret = secret,
            planId = "plan_1",
            priceLabel = "₹99 / month"
        ),
        gateway = gateway,
        entitlements = Entitlements(file),
        clock = now
    )

    // ------------------------------------------------------------------- signatures

    @Test
    fun `a body Razorpay signed is accepted`() {
        val body = webhookBody()
        assertTrue(Signature.matches(body, secret, sign(body)))
    }

    @Test
    fun `a body changed after signing is rejected`() {
        val body = webhookBody()
        val signature = sign(body)
        val tampered = body.replace("\"current_end\":1700000000", "\"current_end\":9999999999")
        assertFalse(
            Signature.matches(tampered, secret, signature),
            "a rewritten expiry must not keep the old signature"
        )
    }

    @Test
    fun `a signature made with the wrong secret is rejected`() {
        val body = webhookBody()
        assertFalse(Signature.matches(body, secret, Signature.hmacSha256(body, "not-the-secret")))
    }

    @Test
    fun `a missing or empty signature is rejected`() {
        val body = webhookBody()
        assertFalse(Signature.matches(body, secret, null))
        assertFalse(Signature.matches(body, secret, ""))
        assertFalse(Signature.matches(body, "", sign(body)))
    }

    // ---------------------------------------------------------------------- parsing

    @Test
    fun `a charged event is read in full`() {
        val event = assertNotNull(Webhook.parse(webhookBody()))
        assertEquals("subscription.charged", event.event)
        assertEquals("sub_test_1", event.subscriptionId)
        assertEquals("active", event.status)
        assertEquals("player-a", event.playerId)
        assertEquals(
            1_700_000_000_000L,
            event.paidUntilMillis,
            "Razorpay counts seconds, this server counts millis"
        )
    }

    @Test
    fun `events that say nothing about paying are ignored`() {
        assertNull(Webhook.parse(webhookBody(event = "payment.authorized")))
        assertNull(Webhook.parse("not json at all"))
        assertNull(Webhook.parse("""{"event":"subscription.charged"}"""))
    }

    // ----------------------------------------------------------------- entitlements

    @Test
    fun `an unsigned webhook grants nothing`() {
        val entitlements = Entitlements()
        val body = webhookBody()
        assertNull(Webhook.handle(body, null, secret, entitlements))
        assertNull(Webhook.handle(body, "deadbeef", secret, entitlements))
        assertEquals(0, entitlements.size(), "nobody should have been given a subscription")
    }

    @Test
    fun `a signed webhook for a subscription this server never made grants nothing`() {
        val entitlements = Entitlements()
        val body = webhookBody(playerNote = null)
        assertNull(
            Webhook.handle(body, sign(body), secret, entitlements),
            "an unknown subscription must not be able to name its own player"
        )
        assertEquals(0, entitlements.size())
    }

    @Test
    fun `a subscription this server started is matched to its player by the claim`() {
        val entitlements = Entitlements()
        entitlements.claimSubscription("sub_test_1", "player-a")
        val body = webhookBody(playerNote = null)

        assertEquals("player-a", Webhook.handle(body, sign(body), secret, entitlements))
        assertTrue(entitlements.isActive("player-a", 1_699_000_000_000L))
    }

    @Test
    fun `the subscription lasts until the cycle ends, plus a day of grace`() {
        val paidUntil = 1_700_000_000_000L
        val entitlement = Entitlement("player-a", "sub_1", "active", paidUntil)

        assertTrue(entitlement.isActive(paidUntil - 1000L), "still inside the paid month")
        assertTrue(
            entitlement.isActive(paidUntil + 1000L),
            "the renewal charge lands hours after the cycle turns over"
        )
        assertFalse(
            entitlement.isActive(paidUntil + Entitlement.GRACE_MILLIS + 1000L),
            "a day later, an unrenewed subscription is over"
        )
    }

    @Test
    fun `a late cancellation cannot take back a month already paid for`() {
        val entitlements = Entitlements()
        entitlements.claimSubscription("sub_test_1", "player-a")

        val charged = webhookBody(event = "subscription.charged", currentEndSeconds = 1_700_000_000L)
        Webhook.handle(charged, sign(charged), secret, entitlements)

        // Razorpay retries and reorders; an older cancellation can arrive afterwards.
        val cancelled = webhookBody(
            event = "subscription.cancelled",
            status = "cancelled",
            currentEndSeconds = 1_600_000_000L
        )
        Webhook.handle(cancelled, sign(cancelled), secret, entitlements)

        val held = assertNotNull(entitlements["player-a"])
        assertEquals(
            1_700_000_000_000L,
            held.paidUntilMillis,
            "the paid month must survive an out of order cancellation"
        )
        assertEquals("cancelled", held.status, "but it must stop renewing")
        assertTrue(held.isActive(1_699_000_000_000L))
    }

    @Test
    fun `a subscription survives the server being restarted`() {
        val file = temporaryFile()
        val first = Entitlements(file)
        first.claimSubscription("sub_test_1", "player-a")
        val body = webhookBody()
        Webhook.handle(body, sign(body), secret, first)

        val afterRestart = Entitlements(file)
        assertTrue(
            afterRestart.isActive("player-a", 1_699_000_000_000L),
            "a redeploy must not make the player pay again"
        )
        assertEquals("player-a", afterRestart.playerFor("sub_test_1"))
    }

    @Test
    fun `a claim made before payment survives a restart too`() {
        val file = temporaryFile()
        Entitlements(file).claimSubscription("sub_pending", "player-b")

        val afterRestart = Entitlements(file)
        assertEquals(
            "player-b",
            afterRestart.playerFor("sub_pending"),
            "a restart between opening the payment page and paying must not lose the player"
        )
    }

    // ---------------------------------------------------------------------- billing

    @Test
    fun `a server with no keys sells nothing and says so`() {
        val unconfigured = Billing(config = BillingConfig(), entitlements = Entitlements())
        assertFalse(unconfigured.isConfigured)
        assertNull(unconfigured.beginSubscription("player-a", "1.2.3.4"))
        assertFalse(unconfigured.statusFor("player-a").active)
    }

    @Test
    fun `starting a subscription hands back the payment page and remembers whose it is`() {
        val gateway = FakeGateway()
        val billing = billing(gateway)

        val created = assertNotNull(billing.beginSubscription("player-a", "1.2.3.4"))
        assertTrue(created.payUrl.startsWith("https://"))
        assertEquals(listOf("player-a"), gateway.created)
        assertEquals("player-a", billing.entitlements.playerFor(created.id))
        assertFalse(
            billing.statusFor("player-a").active,
            "opening the payment page is not paying"
        )
    }

    @Test
    fun `the subscription only turns on once Razorpay says the money arrived`() {
        val billing = billing(now = { 1_699_000_000_000L })
        val created = assertNotNull(billing.beginSubscription("player-a", "1.2.3.4"))

        assertFalse(billing.statusFor("player-a").active)

        val body = webhookBody(subscriptionId = created.id, playerNote = null)
        assertEquals("player-a", billing.handleWebhook(body, sign(body)))

        val status = billing.statusFor("player-a")
        assertTrue(status.active)
        assertEquals("active", status.status)
    }

    @Test
    fun `cancelling stops the renewal at Razorpay`() {
        val gateway = FakeGateway()
        val billing = billing(gateway, now = { 1_699_000_000_000L })
        val created = assertNotNull(billing.beginSubscription("player-a", "1.2.3.4"))
        val body = webhookBody(subscriptionId = created.id, playerNote = null)
        billing.handleWebhook(body, sign(body))

        assertTrue(billing.cancel("player-a"))
        assertEquals(listOf(created.id), gateway.cancelled)
        assertTrue(
            billing.statusFor("player-a").active,
            "cancelling mid month must not end the month"
        )
    }

    @Test
    fun `cancelling something that was never bought does nothing`() {
        val gateway = FakeGateway()
        assertFalse(billing(gateway).cancel("player-nobody"))
        assertTrue(gateway.cancelled.isEmpty())
    }

    @Test
    fun `one address cannot open an unlimited number of subscriptions`() {
        val gateway = FakeGateway()
        val billing = billing(gateway)
        var allowed = 0
        repeat(40) {
            if (billing.beginSubscription("player-$it", "9.9.9.9") != null) allowed++
        }
        assertEquals(6, allowed, "the throttle should have stopped the rest")
        assertEquals(6, gateway.created.size, "and Razorpay should not have seen them")
    }

    @Test
    fun `the throttle lets the same caller back after the window passes`() {
        var now = 1_000_000L
        val throttle = Throttle(limit = 2, windowMillis = 1000L)
        assertTrue(throttle.allow("a", now))
        assertTrue(throttle.allow("a", now))
        assertFalse(throttle.allow("a", now))

        now += 1001L
        assertTrue(throttle.allow("a", now), "the window has passed")
    }

    @Test
    fun `an absurd player id is refused rather than sent to Razorpay`() {
        val gateway = FakeGateway()
        val billing = billing(gateway)
        assertNull(billing.beginSubscription("", "1.2.3.4"))
        assertNull(billing.beginSubscription("x".repeat(500), "1.2.3.4"))
        assertTrue(gateway.created.isEmpty())
    }

    @Test
    fun `a gateway that fails is reported rather than pretended away`() {
        val billing = billing(FakeGateway(failNext = true))
        assertNull(billing.beginSubscription("player-a", "1.2.3.4"))
        assertFalse(billing.statusFor("player-a").active)
    }
}
