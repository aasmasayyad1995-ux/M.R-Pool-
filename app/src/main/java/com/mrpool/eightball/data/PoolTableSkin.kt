package com.mrpool.eightball.data

import com.mrpool.eightball.game.ClothProperties

/** How fast the cloth on a table plays. */
enum class ClothSpeed(val label: String) {
    SLOW("Heavy cloth"),
    NORMAL("Standard cloth"),
    FAST("Tournament cloth");

    fun toProperties(): ClothProperties = when (this) {
        SLOW -> ClothProperties.SLOW
        NORMAL -> ClothProperties.TOURNAMENT
        FAST -> ClothProperties.FAST
    }
}

/**
 * One of the twenty tables in the shop.
 *
 * A table is more than a colour swap: [clothSpeed] changes how the balls actually run, so
 * the heavy cloth in the local club plays slower than the tournament cloth at the top.
 * Matches are free to enter — what a win pays is set by the robot you beat, not the table
 * you beat it on.
 */
data class PoolTableSkin(
    val id: Int,
    val name: String,
    val venue: String,
    val price: Int,
    val clothSpeed: ClothSpeed,
    /** Cloth colour as 0xAARRGGBB. */
    val feltColor: Long,
    /** Rail / frame colour as 0xAARRGGBB. */
    val railColor: Long,
    /** Pocket leather and trim colour as 0xAARRGGBB. */
    val trimColor: Long
) {
    val isFree: Boolean get() = price == 0

    val cloth: ClothProperties get() = clothSpeed.toProperties()

    companion object {
        val ALL: List<PoolTableSkin> = listOf(
            PoolTableSkin(0, "Corner Pocket", "Your local club", 0, ClothSpeed.SLOW,
                0xFF16794Au.toLong(), 0xFF4A2E1Cu.toLong(), 0xFF2A1810u.toLong()),
            PoolTableSkin(1, "Bar Box", "Downtown tavern", 1000, ClothSpeed.SLOW,
                0xFF0F6B5Au.toLong(), 0xFF3E2216u.toLong(), 0xFF1E120Au.toLong()),
            PoolTableSkin(2, "Blue Chalk Hall", "Riverside", 1500, ClothSpeed.NORMAL,
                0xFF1B5FA8u.toLong(), 0xFF412619u.toLong(), 0xFF22140Cu.toLong()),
            PoolTableSkin(3, "Old Oak", "Country inn", 2000, ClothSpeed.SLOW,
                0xFF2C7A3Fu.toLong(), 0xFF6B4423u.toLong(), 0xFF35200Fu.toLong()),
            PoolTableSkin(4, "Crimson Room", "Members only", 2800, ClothSpeed.NORMAL,
                0xFF8E1F33u.toLong(), 0xFF2B1A12u.toLong(), 0xFF150C08u.toLong()),
            PoolTableSkin(5, "Slate Grey", "City league", 3600, ClothSpeed.NORMAL,
                0xFF44505Au.toLong(), 0xFF2F2F33u.toLong(), 0xFF17171Au.toLong()),
            PoolTableSkin(6, "Neon Alley", "Arcade basement", 4500, ClothSpeed.FAST,
                0xFF17284Eu.toLong(), 0xFF191033u.toLong(), 0xFF2CE0FFu.toLong()),
            PoolTableSkin(7, "Sunset Strip", "Rooftop lounge", 5500, ClothSpeed.NORMAL,
                0xFFB04A1Eu.toLong(), 0xFF3A1E12u.toLong(), 0xFFFFB068u.toLong()),
            PoolTableSkin(8, "Ivory Club", "Old money", 6800, ClothSpeed.FAST,
                0xFF6E8F6Au.toLong(), 0xFFE8DCC0u.toLong(), 0xFF8D7A4Eu.toLong()),
            PoolTableSkin(9, "Velvet Lounge", "After hours", 8000, ClothSpeed.NORMAL,
                0xFF5B2A8Cu.toLong(), 0xFF241233u.toLong(), 0xFFC79BFFu.toLong()),
            PoolTableSkin(10, "Steel Yard", "Industrial hall", 9500, ClothSpeed.FAST,
                0xFF2E6E77u.toLong(), 0xFF3C4147u.toLong(), 0xFF9AA6AEu.toLong()),
            PoolTableSkin(11, "Jade Palace", "Far east final", 11000, ClothSpeed.FAST,
                0xFF0E7C63u.toLong(), 0xFF1C2B26u.toLong(), 0xFF6FE3C4u.toLong()),
            PoolTableSkin(12, "Desert Mirage", "Casino floor", 13000, ClothSpeed.FAST,
                0xFFA8862Cu.toLong(), 0xFF43310Fu.toLong(), 0xFFFFE08Au.toLong()),
            PoolTableSkin(13, "Arctic Glass", "Ice hall", 15000, ClothSpeed.FAST,
                0xFF2E7C9Eu.toLong(), 0xFFD6EAF2u.toLong(), 0xFF7FD8F5u.toLong()),
            PoolTableSkin(14, "Black Diamond", "High roller", 18000, ClothSpeed.FAST,
                0xFF16181Cu.toLong(), 0xFF0C0D10u.toLong(), 0xFFB9C4CFu.toLong()),
            PoolTableSkin(15, "Royal Court", "Palace exhibition", 21000, ClothSpeed.FAST,
                0xFF1D2F8Au.toLong(), 0xFF2A1F3Fu.toLong(), 0xFFFFD34Du.toLong()),
            PoolTableSkin(16, "Dragon Hall", "Invitational", 25000, ClothSpeed.FAST,
                0xFF8A1414u.toLong(), 0xFF1A0B0Bu.toLong(), 0xFFFFAF3Cu.toLong()),
            PoolTableSkin(17, "Emerald Throne", "World tour", 30000, ClothSpeed.FAST,
                0xFF0B7A3Bu.toLong(), 0xFF14301Fu.toLong(), 0xFFB9FFCEu.toLong()),
            PoolTableSkin(18, "Platinum Arena", "World final", 40000, ClothSpeed.FAST,
                0xFF3F4A63u.toLong(), 0xFFB8BFCCu.toLong(), 0xFFE9EEF7u.toLong()),
            PoolTableSkin(19, "Mr. Pool Championship", "Hall of fame", 50000, ClothSpeed.FAST,
                0xFF10131Au.toLong(), 0xFF3B2B11u.toLong(), 0xFFFFC93Cu.toLong())
        )

        fun byId(id: Int): PoolTableSkin = ALL.firstOrNull { it.id == id } ?: ALL.first()
    }
}
