package com.leondeklerk.starling.edit.crop

/**
 * Enum class representing aspect ratios.
 * Each ratio has a horizontal [xRatio] and vertical [yRatio] property.
 * Contains helper function to ratio (vertical based on horizontal) or invert ratio (horizontal based on vertical value) values.
 */
enum class AspectRatio(var xRatio: Int, var yRatio: Int) {
    FREE(0, 0),
    SQUARE(1, 1),
    FOUR_THREE(4, 3),
    SIXTEEN_NINE(16, 9),
    TWENTYONE_NINE(21, 9),
    FIVE_FOUR(5, 4),
    ORIGINAL(0, 0);

    /**
     * Create a vertical value based on the given horizontal value.
     * @param src: the horizontal value to ratio.
     * @return the vertical value based on the horizontal value.
     */
    fun ratio(src: Float): Float {
        if (this == FREE) {
            return src
        }
        return (src / xRatio) * yRatio
    }

    /**
     * Create a horizontal value based on the given vertical value (inverted ratio).
     * @param src: the vertical value to ratio.
     * @return the horizontal value based on the vertical value.
     */
    fun ratioInverted(src: Float): Float {
        if (this == FREE) {
            return src
        }
        return (src / yRatio) * xRatio
    }
}
