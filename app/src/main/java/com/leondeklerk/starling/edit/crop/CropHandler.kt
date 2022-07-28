package com.leondeklerk.starling.edit.crop

/**
 * Data class representing the properties of a box handler.
 * @param type: specifies the specific handler
 * @param value: specifies the current value of the handler (x/y)
 * @param outBound: specifies the value of the outer bound of the handler (e.g left bound of the image)
 * @param inBound: specifies the inner bound of the handler (e.g for left this is right - minDimens)
 * @param centerBound: specifies the bound of the center if both handlers on this axis move together towards the center.
 * @param isHorizontal: specifies if the handler is horizontal or not (vertical).
 * @param isOuter: specifies if the handler is an outer handler (right/bottom) or an inner (left/top)
 */
data class CropHandler(
    val type: HandlerType,
    val value: Float,
    val outBound: Float,
    val inBound: Float,
    val centerBound: Float,
    val isHorizontal: Boolean,
    val isOuter: Boolean
)
