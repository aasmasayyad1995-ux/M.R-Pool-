package com.mrpool.eightball

import com.mrpool.eightball.ai.RobotDifficulty
import com.mrpool.eightball.billing.ClaimState
import com.mrpool.eightball.billing.PaymentInstructions
import com.mrpool.eightball.billing.Subscription
import com.mrpool.eightball.billing.SubscriptionPlan
import com.mrpool.eightball.data.CueStick
import com.mrpool.eightball.data.PlayerProfile
import com.mrpool.eightball.data.PoolTableSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The subscription: what it unlocks, what it pays, and what it takes back when it ends.
 *
 * Somebody's money is on the other end of these rules, so they are worth more care than
 * the rest of the shop. The two failures that matter are opposite: handing Pro to a player
 * who has not paid, and taking something away from one who has.
 */
class SubscriptionTest {

    private val free = PlayerProfile()
    private val paid = PlayerProfile(pro = true, proUntilMillis = Long.MAX_VALUE)

    // ------------------------------------------------------------------ what it costs

    @Test
    fun `the free player still has exactly what the shop promised`() {
        assertEquals("the shop sells twelve cues", 12, CueStick.ALL.size)
        assertEquals("and twenty tables", 20, PoolTableSkin.ALL.size)
        assertTrue("none of them is a Pro item", CueStick.ALL.none { it.proOnly })
        assertTrue(PoolTableSkin.ALL.none { it.proOnly })
    }

    @Test
    fun `a Pro item is not free just because it has no price`() {
        for (cue in CueStick.PRO_ONLY) {
            assertFalse("${cue.name} must not read as free", cue.isFree)
            assertFalse("${cue.name} must be locked without a subscription", free.owns(cue))
            assertFalse("and must never be buyable", free.canBuy(cue))
        }
        for (table in PoolTableSkin.PRO_ONLY) {
            assertFalse(table.isFree)
            assertFalse(free.owns(table))
            assertFalse(free.canBuy(table))
        }
    }

    @Test
    fun `Pro items give no advantage a paying shop cue could not`() {
        val bestInShop = CueStick.ALL.maxOf { it.power }
        for (cue in CueStick.PRO_ONLY) {
            assertTrue(
                "${cue.name} hits harder than anything on sale — that is pay to win",
                cue.power <= bestInShop + 1e-6f
            )
        }
        val shopCloths = PoolTableSkin.ALL.map { it.clothSpeed }.toSet()
        for (table in PoolTableSkin.PRO_ONLY) {
            assertTrue("${table.name} plays on cloth nobody else can buy", table.clothSpeed in shopCloths)
        }
    }

    // --------------------------------------------------------------- what it unlocks

    @Test
    fun `a subscription carries the Pro items and nothing else`() {
        for (cue in CueStick.PRO_ONLY) assertTrue("${cue.name} should be unlocked", paid.owns(cue))
        for (table in PoolTableSkin.PRO_ONLY) assertTrue(paid.owns(table))
    }

    @Test
    fun `a subscription buys nothing in the shop`() {
        val expensive = CueStick.ALL.maxBy { it.price }
        assertFalse(
            "the shop is earned with coins, by subscribers and everyone else alike",
            paid.owns(expensive)
        )
        assertTrue("so a subscriber still has to buy it", paid.canBuy(expensive))
        assertFalse("and a free player is in exactly the same position", free.owns(expensive))
    }

    @Test
    fun `a subscriber and a free player who played the same have the same cues`() {
        val earned = setOf(0, CueStick.ALL[3].id, CueStick.ALL[5].id)
        val freePlayer = PlayerProfile(ownedCueIds = earned)
        val subscriber = PlayerProfile(ownedCueIds = earned, pro = true)

        val shopOwnedByFree = CueStick.ALL.filter { freePlayer.owns(it) }.map { it.id }
        val shopOwnedByPro = CueStick.ALL.filter { subscriber.owns(it) }.map { it.id }
        assertEquals(
            "nothing across an online table should have been bought with money",
            shopOwnedByFree,
            shopOwnedByPro
        )
    }

    // ------------------------------------------------------------------ what it pays

    @Test
    fun `the daily bonus is five times bigger with a subscription`() {
        assertEquals(50, free.dailyBonus)
        assertEquals(250, paid.dailyBonus)
    }

    @Test
    fun `beating a robot pays double with a subscription`() {
        assertEquals(25, free.prizeFor(RobotDifficulty.BEGINNER))
        assertEquals(50, free.prizeFor(RobotDifficulty.MEDIUM))
        assertEquals(100, free.prizeFor(RobotDifficulty.HARD))

        assertEquals(50, paid.prizeFor(RobotDifficulty.BEGINNER))
        assertEquals(100, paid.prizeFor(RobotDifficulty.MEDIUM))
        assertEquals(200, paid.prizeFor(RobotDifficulty.HARD))
    }

    @Test
    fun `a subscriber is marked out by name`() {
        val named = free.copy(playerName = "Asad")
        assertEquals("Asad", named.displayName)
        assertNotEquals(
            "a subscriber should look different across the table",
            named.displayName,
            named.copy(pro = true).displayName
        )
        assertTrue(named.copy(pro = true).displayName.startsWith("Asad"))
    }

    // --------------------------------------------------------- when it runs out

    @Test
    fun `coins already spent survive the subscription ending`() {
        val bought = CueStick.ALL.first { it.price > 0 }
        val lapsed = PlayerProfile(ownedCueIds = setOf(0, bought.id), pro = false)
        assertTrue("a cue paid for with coins is the player's for good", lapsed.owns(bought))
    }

    @Test
    fun `a lapsed subscription puts the house cue back in the player's hand`() {
        val proCue = CueStick.PRO_ONLY.first()
        val whilePaid = PlayerProfile(equippedCueId = proCue.id, pro = true)
        assertEquals(proCue.id, whilePaid.equippedCue.id)

        val lapsed = whilePaid.copy(pro = false, proUntilMillis = 0L)
        assertEquals(
            "a cue the player no longer has must not follow them to the table",
            CueStick.ALL.first().id,
            lapsed.equippedCue.id
        )
        assertTrue(lapsed.owns(lapsed.equippedCue))
    }

    @Test
    fun `a lapsed subscription puts the first table back too`() {
        val proTable = PoolTableSkin.PRO_ONLY.first()
        val lapsed = PlayerProfile(equippedTableId = proTable.id, pro = false)
        assertEquals(PoolTableSkin.ALL.first().id, lapsed.equippedTable.id)
    }

    @Test
    fun `a shop item bought with coins stays equipped after the subscription ends`() {
        val bought = CueStick.ALL.first { it.price > 0 }
        val lapsed = PlayerProfile(
            ownedCueIds = setOf(0, bought.id),
            equippedCueId = bought.id,
            pro = false
        )
        assertEquals(
            "the subscription lent the rest, not this one",
            bought.id,
            lapsed.equippedCue.id
        )
    }

    // ------------------------------------------------------ reading the server's word

    @Test
    fun `the server's answer is read as sent`() {
        val parsed = Subscription.parse(
            """{"active":true,"until":1700000000000,"claim":"APPROVED","reference":"MRP-K7J2Q"}"""
        )
        assertTrue(parsed.isActive(1_699_000_000_000L))
        assertFalse("it must expire by itself", parsed.isActive(1_700_000_000_001L))
        assertEquals(ClaimState.APPROVED, parsed.claimState)
        assertEquals("MRP-K7J2Q", parsed.reference)
    }

    @Test
    fun `a payment waiting to be checked is not a subscription`() {
        val waiting = Subscription.parse(
            """{"active":false,"until":0,"claim":"SUBMITTED","reference":"MRP-K7J2Q"}"""
        )
        assertFalse(
            "saying you have paid must never unlock anything",
            waiting.isActive(System.currentTimeMillis())
        )
        assertTrue("but the screen should be able to say it is being checked", waiting.isWaitingOnOwner)
    }

    @Test
    fun `nothing the server did not say grants a subscription`() {
        val nothing = listOf(
            null,
            "",
            "not json",
            "<html>captive portal login</html>",
            """{"active":false,"until":0,"claim":"SUBMITTED"}""",
            """{"active":true}""",
            """{"active":true,"until":0,"claim":"APPROVED"}"""
        )
        for (body in nothing) {
            val parsed = Subscription.parse(body)
            assertFalse(
                "\"$body\" must not turn into a subscription",
                parsed.isActive(System.currentTimeMillis())
            )
        }
    }

    @Test
    fun `a rejected payment leaves the player exactly where they were`() {
        val parsed = Subscription.parse(
            """{"active":false,"until":0,"claim":"REJECTED","reference":"MRP-K7J2Q"}"""
        )
        assertFalse(parsed.isActive(System.currentTimeMillis()))
        assertEquals(ClaimState.REJECTED, parsed.claimState)
        assertFalse(parsed.isWaitingOnOwner)
    }

    @Test
    fun `the payment instructions are read, or refused outright`() {
        val given = PaymentInstructions.parse(
            """{"reference":"MRP-K7J2Q","payLink":"upi://pay?pa=a%40b&am=99&tn=MRP-K7J2Q",""" +
                """"upiId":"a@b","price":"₹99 / month"}"""
        )
        assertEquals("MRP-K7J2Q", given!!.reference)
        assertTrue(given.payLink.startsWith("upi://"))
        assertEquals("a@b", given.upiId)

        // Without a reference or a link there is nothing a player could pay, so refusing
        // beats showing a payment screen that cannot lead anywhere.
        assertEquals(null, PaymentInstructions.parse(null))
        assertEquals(null, PaymentInstructions.parse("nonsense"))
        assertEquals(null, PaymentInstructions.parse("""{"payLink":"upi://pay"}"""))
        assertEquals(null, PaymentInstructions.parse("""{"reference":"MRP-K7J2Q"}"""))
    }

    @Test
    fun `a server that cannot sell anything says so instead of pretending`() {
        assertFalse(SubscriptionPlan.parse(null).configured)
        assertFalse(SubscriptionPlan.parse("garbage").configured)
        assertFalse(SubscriptionPlan.parse("""{"configured":false,"price":""}""").configured)

        val live = SubscriptionPlan.parse(
            """{"configured":true,"price":"₹99 / month","upiId":"asad@okhdfcbank"}"""
        )
        assertTrue(live.configured)
        assertEquals("₹99 / month", live.price)
        assertEquals("asad@okhdfcbank", live.upiId)
    }
}
