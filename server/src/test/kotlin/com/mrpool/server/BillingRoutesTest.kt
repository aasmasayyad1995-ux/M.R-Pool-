package com.mrpool.server

import com.mrpool.server.billing.Billing
import com.mrpool.server.billing.BillingConfig
import com.mrpool.server.billing.Entitlements
import com.mrpool.server.billing.NewSubscription
import com.mrpool.server.billing.Signature
import com.mrpool.server.billing.SubscriptionGateway
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The subscription over real HTTP, through the real routing.
 *
 * [BillingTest] covers the rules; this covers the wiring — the paths, the body the app
 * sends, and the header Razorpay signs with. Those are exactly the things that compile
 * perfectly and are still wrong.
 */
class BillingRoutesTest {

    private val secret = "whsec_routes"

    private class FakeGateway : SubscriptionGateway {
        override fun createSubscription(playerId: String) =
            NewSubscription("sub_routed", "https://rzp.io/i/routed")

        override fun cancelSubscription(subscriptionId: String) = true
    }

    private fun billing(): Billing = Billing(
        config = BillingConfig(
            keyId = "rzp_test",
            keySecret = "secret",
            webhookSecret = secret,
            planId = "plan_1",
            priceLabel = "₹99 / month"
        ),
        gateway = FakeGateway(),
        entitlements = Entitlements(),
        clock = { 1_699_000_000_000L }
    )

    private fun chargedBody(subscriptionId: String) = """
        {"entity":"event","event":"subscription.charged",
         "payload":{"subscription":{"entity":{"id":"$subscriptionId","status":"active",
         "current_end":1700000000,"notes":{}}}}}
    """.trimIndent().replace("\n", "")

    @Test
    fun `a server with no keys says subscriptions are unavailable`() = testApplication {
        application { matchServer() }

        val plan = client.get("/billing/plan")
        assertEquals(HttpStatusCode.OK, plan.status)
        assertTrue(
            plan.bodyAsText().contains("\"configured\":false"),
            "the app has to be able to tell, rather than failing at a dead endpoint"
        )

        val attempt = client.post("/billing/subscribe") { setBody("""{"player":"p1"}""") }
        assertEquals(HttpStatusCode.ServiceUnavailable, attempt.status)
    }

    @Test
    fun `paying end to end, from the payment page to an active subscription`() = testApplication {
        application { matchServer(billing = billing()) }

        val started = client.post("/billing/subscribe") { setBody("""{"player":"p1"}""") }
        assertEquals(HttpStatusCode.OK, started.status)
        assertTrue(started.bodyAsText().contains("https://rzp.io/i/routed"))

        assertTrue(
            client.get("/billing/status?player=p1").bodyAsText().contains("\"active\":false"),
            "opening the page is not paying"
        )

        val body = chargedBody("sub_routed")
        val accepted = client.post("/billing/webhook") {
            header("X-Razorpay-Signature", Signature.hmacSha256(body, secret))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, accepted.status)

        assertTrue(
            client.get("/billing/status?player=p1").bodyAsText().contains("\"active\":true"),
            "the webhook should have turned the subscription on"
        )
    }

    @Test
    fun `a webhook nobody signed is refused and grants nothing`() = testApplication {
        application { matchServer(billing = billing()) }
        client.post("/billing/subscribe") { setBody("""{"player":"p1"}""") }

        val body = chargedBody("sub_routed")
        val forged = client.post("/billing/webhook") {
            header("X-Razorpay-Signature", "0".repeat(64))
            setBody(body)
        }
        assertEquals(HttpStatusCode.BadRequest, forged.status)

        val unsigned = client.post("/billing/webhook") { setBody(body) }
        assertEquals(HttpStatusCode.BadRequest, unsigned.status)

        assertTrue(
            client.get("/billing/status?player=p1").bodyAsText().contains("\"active\":false"),
            "a forged webhook must never hand out a subscription"
        )
    }

    @Test
    fun `asking about nobody is a bad request, not a free subscription`() = testApplication {
        application { matchServer(billing = billing()) }
        assertEquals(HttpStatusCode.BadRequest, client.get("/billing/status").status)
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/billing/subscribe") { setBody("{}") }.status
        )
    }
}
