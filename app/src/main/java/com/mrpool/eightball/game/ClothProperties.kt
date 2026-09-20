package com.mrpool.eightball.game

/**
 * Per-table feel. Every purchasable table maps onto one of these, so a cheap bar box
 * plays slower and bouncier than a tournament table.
 */
data class ClothProperties(
    /** Deceleration of a sliding ball, m/s^2. */
    val slidingFriction: Float = 2.6f,
    /** Deceleration of a naturally rolling ball, m/s^2. */
    val rollingFriction: Float = 0.62f,
    /** Exponential decay rate of english, 1/s. */
    val spinDecay: Float = 4.2f,
    /** Energy kept when bouncing off a cushion. */
    val cushionRestitution: Float = 0.76f,
    /** Energy kept in a ball to ball collision. */
    val ballRestitution: Float = 0.95f,
    /** Friction coefficient between two colliding balls (produces throw). */
    val ballFriction: Float = 0.06f,
    /** How strongly english grabs the cushion. */
    val cushionSpinTransfer: Float = 0.38f
) {
    companion object {
        val TOURNAMENT = ClothProperties()
        val FAST = ClothProperties(
            slidingFriction = 2.4f,
            rollingFriction = 0.52f,
            cushionRestitution = 0.80f
        )
        val SLOW = ClothProperties(
            slidingFriction = 2.9f,
            rollingFriction = 0.78f,
            cushionRestitution = 0.71f
        )
    }
}
