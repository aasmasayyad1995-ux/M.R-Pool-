package com.mrpool.eightball.ai

/**
 * How well the robot plays.
 *
 * [BEGINNER] misses a lot and never plans ahead, [MEDIUM] is a solid club player, and
 * [HARD] rehearses every candidate shot in the physics engine, plays position for the next
 * ball and locks the player up with a safety when there is nothing on.
 */
enum class RobotDifficulty(
    val label: String,
    val blurb: String,
    /** Standard deviation of the aiming error, in radians. */
    val aimError: Float,
    /** Standard deviation of the speed error, as a fraction of the intended speed. */
    val powerError: Float,
    /** Chance of choosing something other than the strongest available shot. */
    val blunderChance: Float,
    /** How many candidate shots are rehearsed in the physics engine (0 = none). */
    val lookAheadCandidates: Int,
    /** How much the robot values where the cue ball finishes. */
    val positionWeight: Float,
    /** Whether the robot will play a deliberate safety instead of a hopeless pot. */
    val playsSafeties: Boolean,
    /** Whether the robot uses english and follow / draw. */
    val usesSpin: Boolean,
    /** Seconds the robot appears to think for. */
    val thinkSeconds: Float,
    /** Coins paid for beating this robot. Beating a better robot is worth more. */
    val reward: Int
) {
    BEGINNER(
        label = "Beginner",
        blurb = "Just learning. Aims roughly, hits too hard and forgets about the next ball.",
        aimError = 0.033f,
        powerError = 0.26f,
        blunderChance = 0.38f,
        lookAheadCandidates = 0,
        positionWeight = 0f,
        playsSafeties = false,
        usesSpin = false,
        thinkSeconds = 0.9f,
        reward = 25
    ),
    MEDIUM(
        label = "Medium",
        blurb = "A strong club player. Picks the right ball, controls speed and rarely fouls.",
        aimError = 0.011f,
        powerError = 0.10f,
        blunderChance = 0.10f,
        lookAheadCandidates = 6,
        positionWeight = 0.45f,
        playsSafeties = true,
        usesSpin = true,
        thinkSeconds = 1.3f,
        reward = 50
    ),
    HARD(
        label = "Hard",
        blurb = "Champion level. Rehearses every shot, runs the table and hooks you when it can't.",
        aimError = 0.0016f,
        powerError = 0.022f,
        blunderChance = 0.0f,
        lookAheadCandidates = 14,
        positionWeight = 1.0f,
        playsSafeties = true,
        usesSpin = true,
        thinkSeconds = 1.7f,
        reward = 100
    );

    companion object {
        fun fromName(name: String?): RobotDifficulty =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
    }
}
