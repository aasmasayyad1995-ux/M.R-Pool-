package com.mrpool.eightball.game

/**
 * Impacts reported by [PoolPhysics] as a shot plays out, so the audio layer can make a
 * noise at the moment the balls actually touch.
 *
 * Deliberately not part of [ShotEvents]: the rules only care about what happened by the
 * time the table stopped, while this fires live, many times per shot.
 *
 * The robot rehearses shots on a copy of the table and that copy never carries a listener,
 * so a look ahead stays silent.
 */
interface CollisionListener {

    /** Two balls touched. [speed] is the closing speed along the line of centres, m/s. */
    fun onBallCollision(speed: Float, position: Vec2, cueBallInvolved: Boolean)

    /** A ball hit a cushion. [speed] is the speed into the rail, m/s. */
    fun onCushionCollision(speed: Float, position: Vec2)

    /** A ball dropped. [speed] is how fast it was travelling as it fell. */
    fun onPocketed(ballNumber: Int, speed: Float)

    /** The cue tip struck the cue ball. [speed] is the shot speed in m/s. */
    fun onCueStrike(speed: Float)
}
