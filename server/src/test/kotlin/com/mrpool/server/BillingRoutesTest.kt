package com.mrpool.server

import com.mrpool.server.billing.Billing
import com.mrpool.server.billing.BillingConfig
import com.mrpool.server.billing.Entitlements
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The subscription over real HTTP, through the real routing.
 *
 * [BillingTest] covers the rules; this covers the wiring and the one page a person opens
 * in a browser — the two places where code that compiles perfectly is still wrong.
 */
class BillingRoutesTest {

    private val token = "admin-password-long-enough"

    private fun billing(): Billing = Billing(
        config = BillingConfig(
            upiId = "asad@okhdfcbank",
            payeeName = "Mr. Pool",
            amount = "99",
            adminToken = token,
            days = 30,
            storePath = ""
        ),
        entitlements = Entitlements(),
        clock = { 1_700_000_000_000L },
        random = Random(3)
    )

    /** Digs the reference out of a `/billing/payment` reply. */
    private fun referenceIn(body: String): String =
        Regex(""""reference":"([^"]+)"""").find(body)!!.groupValues[1]

    @Test
    fun `a server with no UPI id says subscriptions are unavailable`() = testApplication {
        application { matchServer() }

        val plan = client.get("/billing/plan")
        assertEquals(HttpStatusCode.OK, plan.status)
        assertTrue(
            plan.bodyAsText().contains("\"configured\":false"),
            "the app has to be able to tell, rather than failing at a dead endpoint"
        )

        val attempt = client.post("/billing/payment") { setBody("""{"player":"p1"}""") }
        assertEquals(HttpStatusCode.ServiceUnavailable, attempt.status)
    }

    @Test
    fun `paying end to end, from the UPI link to an approved subscription`() = testApplication {
        val service = billing()
        application { matchServer(billing = service) }

        val started = client.post("/billing/payment") { setBody("""{"player":"p1"}""") }
        assertEquals(HttpStatusCode.OK, started.status)
        val body = started.bodyAsText()
        assertTrue(body.contains("upi:"), "the player needs a link their UPI app can open")
        val reference = referenceIn(body)

        assertTrue(
            client.get("/billing/status?player=p1").bodyAsText().contains("\"active\":false"),
            "being given a link is not paying"
        )

        val claimed = client.post("/billing/claim") {
            setBody("""{"player":"p1","reference":"$reference","utr":"402312345678"}""")
        }
        assertEquals(HttpStatusCode.OK, claimed.status)
        assertTrue(
            client.get("/billing/status?player=p1").bodyAsText().contains("\"active\":false"),
            "saying you paid is still not paying — only the owner decides"
        )

        val decided = client.post("/billing/admin/decide") {
            setBody("""{"token":"$token","reference":"$reference","approve":true}""")
        }
        assertEquals(HttpStatusCode.OK, decided.status)

        assertTrue(
            client.get("/billing/status?player=p1").bodyAsText().contains("\"active\":true"),
            "the owner's approval should have turned the subscription on"
        )
    }

    @Test
    fun `nobody without the password can approve anything`() = testApplication {
        val service = billing()
        application { matchServer(billing = service) }

        val started = client.post("/billing/payment") { setBody("""{"player":"p1"}""") }
        val reference = referenceIn(started.bodyAsText())
        client.post("/billing/claim") {
            setBody("""{"player":"p1","reference":"$reference","utr":"1"}""")
        }

        for (guess in listOf("", "wrong", token.dropLast(1))) {
            val attempt = client.post("/billing/admin/decide") {
                setBody("""{"token":"$guess","reference":"$reference","approve":true}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, attempt.status)
        }
        val noToken = client.post("/billing/admin/decide") {
            setBody("""{"reference":"$reference","approve":true}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, noToken.status)

        assertTrue(
            client.get("/billing/status?player=p1").bodyAsText().contains("\"active\":false"),
            "a guessed password must never hand out a subscription"
        )
        assertEquals(HttpStatusCode.Unauthorized, client.get("/billing/admin").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/billing/admin?token=wrong").status)
    }

    @Test
    fun `the approvals page shows what is waiting`() = testApplication {
        val service = billing()
        application { matchServer(billing = service) }

        val started = client.post("/billing/payment") { setBody("""{"player":"p1"}""") }
        val reference = referenceIn(started.bodyAsText())
        client.post("/billing/claim") {
            setBody("""{"player":"p1","reference":"$reference","utr":"402312345678"}""")
        }

        val page = client.get("/billing/admin?token=$token")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue(html.contains(reference), "the owner needs the reference to match the payment")
        assertTrue(html.contains("402312345678"), "and the transaction number")
        assertTrue(html.contains("Approve"))
    }

    @Test
    fun `a transaction number cannot smuggle script into the owner's browser`() = testApplication {
        val service = billing()
        application { matchServer(billing = service) }

        val started = client.post("/billing/payment") { setBody("""{"player":"p1"}""") }
        val reference = referenceIn(started.bodyAsText())
        client.post("/billing/claim") {
            setBody("""{"player":"p1","reference":"$reference","utr":"<script>alert(1)</script>"}""")
        }

        val html = client.get("/billing/admin?token=$token").bodyAsText()
        assertFalse(
            html.contains("<script>alert(1)</script>"),
            "this page is opened by the one person who can grant subscriptions"
        )
        assertTrue(html.contains("&lt;script&gt;"), "it should be shown, escaped, not dropped")
    }

    @Test
    fun `asking about nobody is a bad request, not a free subscription`() = testApplication {
        application { matchServer(billing = billing()) }
        assertEquals(HttpStatusCode.BadRequest, client.get("/billing/status").status)
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/billing/payment") { setBody("{}") }.status
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/billing/claim") { setBody("""{"player":"p1","reference":"MRP-NOPE"}""") }.status
        )
    }
}
