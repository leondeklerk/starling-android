package com.leondeklerk.starling.media.gestures

/**
 * The direction a specific swipe is in.
 */
enum class SwipeDirection {
    LEFT,
    UP,
    RIGHT,
    DOWN,
    NONE;

    companion object {
        /**
         * Given an angle produce 4 sections related to the direction.
         * @param angle the angle of the movement.
         * @return the direction of the movement based on the angle.
         */
        fun from(angle: Double): SwipeDirection {
            return when (angle) {
                in 0.0..45.0, in 315.0..360.0 -> RIGHT
                in 45.0..135.0 -> UP
                in 135.0..225.0 -> LEFT
                in 225.0..315.0 -> DOWN
                else -> NONE
            }
        }
    }
}
