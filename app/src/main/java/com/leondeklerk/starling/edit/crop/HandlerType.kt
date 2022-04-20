package com.leondeklerk.starling.edit.crop

/**
 * Specifies the different types of touch handlers available.
 * Each handler represents a specific point on a box.
 * The corners are specified by left/right_top/bottom.
 * The box type relates to a location within the box.
 */
enum class HandlerType {
    TOP,
    RIGHT,
    BOTTOM,
    LEFT,
    LEFT_TOP,
    RIGHT_TOP,
    RIGHT_BOTTOM,
    LEFT_BOTTOM,
    BOX,
    NONE;

    fun parseToSide(type: HandlerType): Side {
        return when (type) {
            LEFT -> Side.LEFT
            TOP -> Side.TOP
            RIGHT -> Side.RIGHT
            BOTTOM -> Side.BOTTOM
            else -> Side.NONE
        }
    }
}
