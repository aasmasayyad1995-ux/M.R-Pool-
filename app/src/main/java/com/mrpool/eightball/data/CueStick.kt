package com.mrpool.eightball.data

/**
 * One of the twelve cues in the shop.
 *
 * The stats are not decoration: [power] scales the top of the power bar, [aim] scales the
 * length of the guide line and [spin] caps how much english the player can dial in.
 */
data class CueStick(
    val id: Int,
    val name: String,
    val price: Int,
    /** 1..5 stars, used for the shop card. */
    val powerStars: Int,
    val aimStars: Int,
    val spinStars: Int,
    /** Butt / wrap colour as 0xAARRGGBB. */
    val buttColor: Long,
    /** Shaft colour as 0xAARRGGBB. */
    val shaftColor: Long,
    /** Ring and inlay colour as 0xAARRGGBB. */
    val accentColor: Long,
    val tagline: String
) {
    /** Multiplier applied to the maximum shot speed. */
    val power: Float get() = 1f + (powerStars - 1) * 0.035f

    /** Multiplier applied to the length of the aiming guide. */
    val aim: Float get() = 1f + (aimStars - 1) * 0.30f

    /** Maximum english the player can apply, 0..1. */
    val spin: Float get() = 0.45f + (spinStars - 1) * 0.1375f

    val isFree: Boolean get() = price == 0

    companion object {
        /** The twelve cues, cheapest first. The starter cue is free; the rest begin at 200. */
        val ALL: List<CueStick> = listOf(
            CueStick(
                id = 0, name = "House Cue", price = 0,
                powerStars = 2, aimStars = 2, spinStars = 2,
                buttColor = 0xFF6B4A2Fu.toLong(), shaftColor = 0xFFD9B487u.toLong(),
                accentColor = 0xFF3A2416u.toLong(),
                tagline = "The battered cue off the wall. It still pots balls."
            ),
            CueStick(
                id = 1, name = "Rookie Maple", price = 200,
                powerStars = 2, aimStars = 3, spinStars = 2,
                buttColor = 0xFF8A5A33u.toLong(), shaftColor = 0xFFE7C89Bu.toLong(),
                accentColor = 0xFFF4C542u.toLong(),
                tagline = "Straight grain maple. Forgiving and honest."
            ),
            CueStick(
                id = 2, name = "Blue Chalk", price = 350,
                powerStars = 3, aimStars = 3, spinStars = 2,
                buttColor = 0xFF1F4E8Cu.toLong(), shaftColor = 0xFFDCC49Cu.toLong(),
                accentColor = 0xFFBFD9FFu.toLong(),
                tagline = "Chalked, ready, and quietly confident."
            ),
            CueStick(
                id = 3, name = "Crimson Break", price = 500,
                powerStars = 4, aimStars = 2, spinStars = 2,
                buttColor = 0xFF8C1F2Fu.toLong(), shaftColor = 0xFFE0C49Au.toLong(),
                accentColor = 0xFFFFD7D7u.toLong(),
                tagline = "Built for one job: smashing the rack apart."
            ),
            CueStick(
                id = 4, name = "Emerald Edge", price = 750,
                powerStars = 3, aimStars = 4, spinStars = 3,
                buttColor = 0xFF12543Au.toLong(), shaftColor = 0xFFE3C79Fu.toLong(),
                accentColor = 0xFF6FE3B0u.toLong(),
                tagline = "A cue that makes long pots look routine."
            ),
            CueStick(
                id = 5, name = "Midnight Carbon", price = 1000,
                powerStars = 4, aimStars = 4, spinStars = 3,
                buttColor = 0xFF14171Cu.toLong(), shaftColor = 0xFF2B3038u.toLong(),
                accentColor = 0xFF7FF0FFu.toLong(),
                tagline = "Carbon shaft. Zero deflection, zero excuses."
            ),
            CueStick(
                id = 6, name = "Royal Ivory", price = 1400,
                powerStars = 3, aimStars = 5, spinStars = 4,
                buttColor = 0xFF3A2C4Fu.toLong(), shaftColor = 0xFFF2E6CFu.toLong(),
                accentColor = 0xFFE6C6FFu.toLong(),
                tagline = "Inlaid, balanced and ruthlessly accurate."
            ),
            CueStick(
                id = 7, name = "Desert Storm", price = 1800,
                powerStars = 5, aimStars = 3, spinStars = 3,
                buttColor = 0xFFB07A2Fu.toLong(), shaftColor = 0xFFEBD3A4u.toLong(),
                accentColor = 0xFFFFE9A8u.toLong(),
                tagline = "Breaks like a sandstorm through the rack."
            ),
            CueStick(
                id = 8, name = "Neon Viper", price = 2400,
                powerStars = 4, aimStars = 4, spinStars = 5,
                buttColor = 0xFF1B1030u.toLong(), shaftColor = 0xFFDCC7A2u.toLong(),
                accentColor = 0xFF39FF88u.toLong(),
                tagline = "Screws the cue ball back across the table."
            ),
            CueStick(
                id = 9, name = "Golden Hour", price = 3000,
                powerStars = 4, aimStars = 5, spinStars = 4,
                buttColor = 0xFF6E4A12u.toLong(), shaftColor = 0xFFF1DDB4u.toLong(),
                accentColor = 0xFFFFC93Cu.toLong(),
                tagline = "Gold ringed, tournament proven."
            ),
            CueStick(
                id = 10, name = "Phantom Ice", price = 4000,
                powerStars = 5, aimStars = 5, spinStars = 4,
                buttColor = 0xFF0E2B33u.toLong(), shaftColor = 0xFFCFE9F2u.toLong(),
                accentColor = 0xFFA8F0FFu.toLong(),
                tagline = "Cold, silent, and never leaves the black."
            ),
            CueStick(
                id = 11, name = "Mr. Pool Legend", price = 5000,
                powerStars = 5, aimStars = 5, spinStars = 5,
                buttColor = 0xFF2A0D0Du.toLong(), shaftColor = 0xFFF6E3BCu.toLong(),
                accentColor = 0xFFFF4D2Du.toLong(),
                tagline = "The cue they name the hall of fame after."
            )
        )


        fun byId(id: Int): CueStick = ALL.firstOrNull { it.id == id } ?: ALL.first()
    }
}
