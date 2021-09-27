package com.leondeklerk.starling.edit

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