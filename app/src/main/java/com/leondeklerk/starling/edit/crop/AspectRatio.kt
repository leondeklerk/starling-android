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

    /**
     * Based on a target width and a target height,
     * creates a rectangle that fits within the target with the given aspect ratio.
     * The rectangle is as large as possible, meaning at least one side fits the target.
     */
    fun getSizeWithinFrom(targetW: Int, targetH: Int): Pair<Int, Int> {
        // Start with the target with and ratio the height accordingly
        var pWidth = targetW.toFloat()
        var pHeight = ratio(pWidth)

        // If the height is now larger than possible, it needs to be shrunk by a factor, same case for the width.
        if (pHeight > targetH) {
            // Get the scaling factor (e.g 100 / 150 -> 0.67)
            val factor = targetH / pHeight
            pHeight = targetH.toFloat()
            // Also shrink the width with the factor
            pWidth *= factor
        }
        return Pair(pWidth.toInt(), pHeight.toInt())
    }
}
