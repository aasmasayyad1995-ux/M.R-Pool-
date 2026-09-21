package com.mrpool.server.billing

import java.io.File

/**
 * Everything the subscription needs, read from the environment.
 *
 * There is no payment gateway here. Money arrives straight in the owner's UPI account,
 * which means nothing tells this server that it arrived — so a payment becomes a
 * subscription only when the owner looks at their bank and says so. That is the trade:
 * no commission and no KYC beyond a bank account, in exchange for doing the checking by
 * hand.
 */
data class BillingConfig(
    /** The owner's UPI id, e.g. `name@okhdfcbank`. */
    val upiId: String = System.getenv("UPI_ID").orEmpty().trim(),
    /** The name a UPI app shows the player before they pay. */
    val payeeName: String = System.getenv("UPI_PAYEE_NAME").orEmpty().trim()
        .ifBlank { "Mr. Pool" },
    /** Rupees per month, as a plain number: "99". */
    val amount: String = System.getenv("SUBSCRIPTION_PRICE").orEmpty().trim(),
    /**
     * The password for the approvals page.
     *
     * Without it the owner's admin page is open to the internet, and anyone who finds it
     * can hand out subscriptions — so a blank one switches subscriptions off entirely
     * rather than leaving the page unguarded.
     */
    val adminToken: String = System.getenv("ADMIN_TOKEN").orEmpty(),
    /** How long one payment buys. */
    val days: Int = System.getenv("SUBSCRIPTION_DAYS")?.toIntOrNull() ?: 30,
    /** Where paid players are remembered. Must outlive the container. */
    val storePath: String = System.getenv("SUBSCRIPTION_STORE").orEmpty()
) {
    val isConfigured: Boolean
        get() = upiId.isNotBlank() && amount.isNotBlank() && adminToken.isNotBlank()

    fun storeFile(): File? = storePath.takeIf { it.isNotBlank() }?.let(::File)

    /** What the app shows: "₹99 / month". */
    val priceLabel: String get() = if (amount.isBlank()) "" else "₹$amount / month"

    /**
     * The `upi://pay` link a phone hands to GPay, PhonePe or Paytm.
     *
     * [reference] goes in the transaction note, which is the only thing tying a payment in
     * a bank statement back to a player, so it has to survive the round trip intact.
     */
    fun payLink(reference: String): String = buildString {
        append("upi://pay?pa=").append(encode(upiId))
        append("&pn=").append(encode(payeeName))
        append("&am=").append(encode(amount))
        append("&cu=INR")
        append("&tn=").append(encode(reference))
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** Where a player's payment has got to. */
enum class ClaimState {
    /** The player was given a reference and a link; nothing has been paid yet. */
    AWAITING,

    /** The player says they have paid. Waiting for the owner to check the bank. */
    SUBMITTED,

    /** The owner found the money. The subscription is on. */
    APPROVED,

    /** The owner did not find the money. */
    REJECTED
}

/**
 * One player's attempt to pay.
 *
 * @param reference the short code that goes in the UPI transaction note
 * @param utr what the player typed from their UPI app, for the owner to match against
 */
data class Claim(
    val reference: String,
    val playerId: String,
    val state: ClaimState,
    val createdAtMillis: Long,
    val utr: String = "",
    val note: String = ""
)
