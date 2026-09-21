package com.mrpool.eightball

import com.mrpool.eightball.ai.RobotDifficulty
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
    fun `a subscription unlocks every cue and every table`() {
        for (cue in CueStick.EVERY) assertTrue("${cue.name} should be unlocked", paid.owns(cue))
        for (table in PoolTableSkin.EVERY) assertTrue(paid.owns(table))
    }

    @Test
    fun `a subscription cannot be spent on the shop`() {
        val expensive = CueStick.ALL.maxBy { it.price }
        assertTrue(paid.owns(expensive))
        assertFalse("already unlocked, so there is nothing to buy", paid.canBuy(expensive))
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
        val parsed = Subscription.parse("""{"active":true,"until":1700000000000,"status":"active"}""")
        assertTrue(parsed.isActive(1_699_000_000_000L))
        assertFalse("it must expire by itself", parsed.isActive(1_700_000_000_001L))
        assertEquals("active", parsed.status)
    }

    @Test
    fun `nothing the server did not say grants a subscription`() {
        val nothing = listOf(
            null,
            "",
            "not json",
            "<html>captive portal login</html>",
            """{"active":false,"until":0,"status":"none"}""",
            """{"active":true}""",
            """{"active":true,"until":0,"status":"active"}"""
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
    fun `a cancelled subscription still runs to the end of the month it paid for`() {
        val parsed = Subscription.parse(
            """{"active":true,"until":1700000000000,"status":"cancelled"}"""
        )
        assertTrue("the month was paid for", parsed.isActive(1_699_000_000_000L))
        assertTrue("but the app should be able to say it is ending", parsed.isEnding)
    }

    @Test
    fun `a server that cannot sell anything says so instead of pretending`() {
        assertFalse(SubscriptionPlan.parse(null).configured)
        assertFalse(SubscriptionPlan.parse("garbage").configured)
        assertFalse(SubscriptionPlan.parse("""{"configured":false,"price":""}""").configured)

        val live = SubscriptionPlan.parse("""{"configured":true,"price":"₹99 / month"}""")
        assertTrue(live.configured)
        assertEquals("₹99 / month", live.price)
    }
}
