package com.mrpool.server.billing

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The subscription endpoints, plus the page the owner approves payments on.
 *
 * There are no accounts in this game, so a player is an install id the app generated for
 * itself. Money arrives by UPI straight into the owner's account and nothing tells this
 * server, so the only thing that grants a subscription is the owner pressing Approve.
 */
fun Route.billingRoutes(billing: Billing) {

    /** What the subscription costs and whether this server can sell it at all. */
    get("/billing/plan") {
        call.respondJson(
            """{"configured":${billing.isConfigured},""" +
                """"price":${json(billing.config.priceLabel)},""" +
                """"upiId":${json(billing.config.upiId)}}"""
        )
    }

    /** Whether this player has paid, and where their last attempt has got to. */
    get("/billing/status") {
        val player = call.request.queryParameters["player"].orEmpty()
        if (player.isBlank()) {
            call.respondJson("""{"error":"player required"}""", HttpStatusCode.BadRequest)
            return@get
        }
        val status = billing.statusFor(player)
        call.respondJson(
            """{"active":${status.active},"until":${status.activeUntilMillis},""" +
                """"claim":${json(status.claimState?.name.orEmpty())},""" +
                """"reference":${json(status.reference)}}"""
        )
    }

    /** Hands the player a reference and the UPI link to pay it with. */
    post("/billing/payment") {
        val body = call.receiveText()
        val player = stringField(body, "player")
        if (player.isNullOrBlank()) {
            call.respondJson("""{"error":"player required"}""", HttpStatusCode.BadRequest)
            return@post
        }
        if (!billing.isConfigured) {
            call.respondJson(
                """{"error":"subscriptions are not set up on this server"}""",
                HttpStatusCode.ServiceUnavailable
            )
            return@post
        }
        val instructions = billing.paymentInstructions(player, call.request.local.remoteHost)
        if (instructions == null) {
            call.respondJson(
                """{"error":"could not start the payment"}""",
                HttpStatusCode.ServiceUnavailable
            )
            return@post
        }
        call.respondJson(
            """{"reference":${json(instructions.reference)},""" +
                """"payLink":${json(instructions.payLink)},""" +
                """"upiId":${json(instructions.upiId)},""" +
                """"price":${json(instructions.priceLabel)}}"""
        )
    }

    /**
     * The player says they have paid.
     *
     * Grants nothing: it moves the claim into the owner's queue. Everything in the body is
     * the player's word, to be read by a person with their bank statement open.
     */
    post("/billing/claim") {
        val body = call.receiveText()
        val player = stringField(body, "player").orEmpty()
        val reference = stringField(body, "reference").orEmpty()
        val utr = stringField(body, "utr").orEmpty()
        val accepted = billing.submitClaim(player, reference, utr)
        call.respondJson(
            """{"submitted":$accepted}""",
            if (accepted) HttpStatusCode.OK else HttpStatusCode.BadRequest
        )
    }

    // ------------------------------------------------------------------- the owner

    /**
     * The approvals page: payments waiting, with a button each.
     *
     * Plain HTML so it works from the owner's phone, next to their UPI app. Everything a
     * player typed is escaped before it goes anywhere near this page — the transaction
     * number is free text from the internet, and this page is opened by the one person who
     * can grant subscriptions.
     */
    get("/billing/admin") {
        val token = call.request.queryParameters["token"]
        if (!billing.isOwner(token)) {
            call.respondText(
                "Not authorised.",
                ContentType.Text.Plain,
                HttpStatusCode.Unauthorized
            )
            return@get
        }
        call.respondText(adminPage(billing, token.orEmpty()), ContentType.Text.Html)
    }

    /** Approve or reject, from the page above. */
    post("/billing/admin/decide") {
        val body = call.receiveText()
        val token = stringField(body, "token")
        if (!billing.isOwner(token)) {
            call.respondJson("""{"error":"not authorised"}""", HttpStatusCode.Unauthorized)
            return@post
        }
        val reference = stringField(body, "reference").orEmpty()
        val approve = body.contains("\"approve\":true")
        val note = stringField(body, "note").orEmpty()
        val done = if (approve) {
            billing.approve(reference, note) != null
        } else {
            billing.reject(reference, note)
        }
        call.respondJson("""{"done":$done}""", if (done) HttpStatusCode.OK else HttpStatusCode.BadRequest)
    }

    /** Takes a subscription back, for a payment that turned out not to be one. */
    post("/billing/admin/revoke") {
        val body = call.receiveText()
        if (!billing.isOwner(stringField(body, "token"))) {
            call.respondJson("""{"error":"not authorised"}""", HttpStatusCode.Unauthorized)
            return@post
        }
        val player = stringField(body, "player").orEmpty()
        billing.revoke(player)
        call.respondJson("""{"revoked":true}""")
    }
}

private val stamp = SimpleDateFormat("d MMM, HH:mm", Locale.UK)

private fun adminPage(billing: Billing, token: String): String {
    val waiting = billing.queue()
    val rows = waiting.joinToString("\n") { claim ->
        """
        <tr>
          <td><code>${escape(claim.reference)}</code></td>
          <td>${escape(stamp.format(Date(claim.createdAtMillis)))}</td>
          <td><code>${escape(claim.utr.ifBlank { "—" })}</code></td>
          <td><code class="pid">${escape(claim.playerId)}</code></td>
          <td>
            <button onclick="decide('${escape(claim.reference)}', true)">Approve</button>
            <button class="no" onclick="decide('${escape(claim.reference)}', false)">Reject</button>
          </td>
        </tr>
        """.trimIndent()
    }

    val history = billing.recent().joinToString("\n") { claim ->
        """<tr><td><code>${escape(claim.reference)}</code></td>
           <td>${escape(claim.state.name)}</td>
           <td>${escape(stamp.format(Date(claim.createdAtMillis)))}</td></tr>"""
    }

    return """
<!doctype html>
<html lang="en"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Mr. Pool — payments</title>
<style>
  :root { color-scheme: dark; }
  body { font-family: system-ui, sans-serif; margin: 0; padding: 16px;
         background: #101416; color: #e8eaec; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  p.sub { color: #9aa4ab; font-size: 13px; margin: 0 0 18px; }
  table { width: 100%; border-collapse: collapse; font-size: 13px; }
  th { text-align: left; color: #9aa4ab; font-weight: 600; padding: 6px 4px;
       border-bottom: 1px solid #2a3136; }
  td { padding: 8px 4px; border-bottom: 1px solid #1c2226; vertical-align: middle; }
  code { font-size: 12px; }
  code.pid { color: #6b7780; }
  button { background: #2c6e4a; color: #fff; border: 0; border-radius: 6px;
           padding: 7px 12px; font-size: 13px; }
  button.no { background: #6e2c2c; margin-left: 6px; }
  .empty { color: #6b7780; padding: 24px 0; }
  .warn { background: #2a2114; border: 1px solid #5a4620; border-radius: 8px;
          padding: 10px 12px; font-size: 13px; color: #d9c18a; margin-bottom: 18px; }
</style></head><body>
<h1>Payments waiting</h1>
<p class="sub">${waiting.size} to check · ${billing.entitlements.size()} subscribers</p>
<div class="warn">Check the reference and amount in your UPI app or bank statement before
approving. Nothing here has verified that any money arrived — only you can.</div>
${
        if (waiting.isEmpty()) """<p class="empty">Nothing waiting.</p>"""
        else """<table><tr><th>Reference</th><th>When</th><th>UTR</th><th>Player</th><th></th></tr>
$rows</table>"""
    }
<h1 style="margin-top:28px">Recent</h1>
<table><tr><th>Reference</th><th>State</th><th>When</th></tr>
$history</table>
<script>
  const token = ${json(token)};
  async function decide(reference, approve) {
    const response = await fetch('/billing/admin/decide', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token: token, reference: reference, approve: approve })
    });
    if (response.ok) location.reload();
    else alert('That did not work.');
  }
</script>
</body></html>
    """.trimIndent()
}

/** Pulls a string field out of a small JSON body without a JSON dependency. */
private fun stringField(body: String, name: String): String? =
    Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(body)
        ?.groupValues?.get(1)
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")

private fun json(value: String): String =
    '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"'

/** Everything a player typed is escaped before it reaches the owner's browser. */
private fun escape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&#39;")

private suspend fun ApplicationCall.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(body, ContentType.Application.Json, status)
}
