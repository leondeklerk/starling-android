package com.leondeklerk.starling.edit.crop

import android.graphics.PointF
import android.graphics.RectF
import com.google.gson.Gson
import kotlin.math.min

/**
 * Data class representing a box.
 * Each box consists of 4 corners ([PointF]) and for sides ([Line].
 * @param minDimens: the minimum distance two sides have to keep between them.
 * @param l: the left side of the rectangle
 * @param t: the top side of the rectangle
 * @param r: the right side of the rectangle
 * @param b: the bottom side of the rectangle
 */
data class Box(
    val minDimens: Float,
    var l: Float,
    var t: Float,
    var r: Float,
    var b: Float,
) {

    /**
     * A [PointF] representing the left top corner of the box.
     */
    val leftTop: PointF
        get() = PointF(l, t)

    /**
     * A [PointF] representing the right top corner of the box.
     */
    val rightTop: PointF
        get() = PointF(r, t)

    /**
     * A [PointF] representing the right bottom corner of the box.
     */
    val rightBottom: PointF
        get() = PointF(r, b)

    /**
     * A [PointF] representing the left bottom corner of the box.
     */
    val leftBottom: PointF
        get() = PointF(l, b)

    /**
     * A [Line] representing the top side of the box.
     */
    val top
        get() = Line(leftTop, rightTop)

    /**
     * A [Line] representing the right side of the box.
     */
    val right
        get() = Line(rightTop, rightBottom)

    /**
     * A [Line] representing the bottom side of the box.
     */
    val bottom
        get() = Line(rightBottom, leftBottom)

    /**
     * A [Line] representing the left side of the box.
     */
    val left
        get() = Line(leftBottom, leftTop)

    /**
     * The height of the box.
     */
    val height: Float
        get() {
            return b - t
        }

    /**
     * The width of the box.
     */
    val width: Float
        get() {
            return r - l
        }

    /**
     * The center point of the box.
     */
    val center: PointF
        get() {
            val centX = l + (r - l) / 2
            val centY = t + (b - t) / 2
            return PointF(centX, centY)
        }

    /**
     * Returns the rectangle representation of the box
     * @return RectF containing the box.
     */
    val rect: RectF
        get() {
            return RectF(l, t, r, b)
        }

    /**
     * A rectangle representing the bounding box around the center restricted by the minDimens value.
     * When two sides move towards each other (e.g left and right), they have to keep minDimens distance.
     * This is the same as having half of the minDimens on each side of the center value.
     */
    val centerBound: RectF
        get() {
            return RectF(
                center.x - minDimens / 2f,
                center.y - minDimens / 2f,
                center.x + minDimens / 2f,
                center.y + minDimens / 2f
            )
        }

    /**
     * A rectangle represent the inner bounds of a side.
     * Meaning the side + the minDimens property.
     * This is also the maximum value an opposing side can take.
     * E.g the right can never move further than the left + minDimens
     */
    val innerBound: RectF
        get() {
            return RectF(
                l + minDimens,
                t + minDimens,
                r - minDimens,
                b - minDimens
            )
        }

    /**
     * Move the box by a given delta x and y.
     * @param dX: the delta x to move left/right
     * @param dY: the delta y to move top/bottom.
     * @return the current instance.
     */
    fun move(dX: Float, dY: Float): Box {
        l += dX
        r += dX
        t += dY
        b += dY
        return this
    }

    /**
     * Grows the box to a specified width and height.
     * Each side will grow equally (total difference is divided over sides).
     * @param wTo: the target width.
     * @param hTo: the target height.
     * @return the current instance.
     */
    fun growTo(wTo: Float, hTo: Float): Box {
        val wDiff = wTo - width
        val hDiff = hTo - height
        l -= wDiff / 2f
        r += wDiff / 2f
        t -= hDiff / 2f
        b += hDiff / 2f
        return this
    }

    /**
     * Shrink a box by a given delta on each side.
     * Shrinking is seen as subtracting the delta from each side.
     * @param dX: the delta value for the left/right sides
     * @param dY: the delta value for the top/bottom sides.
     * @return the current instance
     */
    fun shrink(dX: Float, dY: Float): Box {
        l += dX
        r -= dX
        t += dY
        b -= dY
        return this
    }

    /**
     * Check if a specific point is within the box.
     * @param x: the x coordinate of the point
     * @param y the y coordinate of the point
     * @return if the point is within the box or not
     */
    fun isWithin(x: Float, y: Float): Boolean {
        return (x in l..r) && (y in t..b)
    }

    /**
     * Copy a border box into a new instance
     * @return a new box instance
     */
    fun copy(): Box {
        val json = Gson().toJson(this)
        return Gson().fromJson(json, Box::class.java)
    }

    companion object {
        /**
         * Based on the given bounds and aspect ratio,
         * calculate the largest possible starting size of the box.
         * Free/Original aspect ratios can just return the bounds as this will always fit.
         * Create a new box instance based on this value.
         * @param bounds the container bounds to construct the box from.
         * @param aspectRatio the ratio of the box to comply with.
         * @param minDP: the minimum size in DP, will only be used if > 1/4 of the width and height
         * @return a box instance that fits in the bounds, based on the aspect ratio.
         */
        fun from(bounds: RectF, aspectRatio: AspectRatio, minDP: Float): Box {
            val base = RectF(bounds)

            // Calculate the starting rectangle based on the aspect ratio
            if (aspectRatio != AspectRatio.FREE && aspectRatio != AspectRatio.ORIGINAL) {
                var xDiff = 0f
                var yDiff = 0f

                // Create a projected value based on the smallest side.
                if (bounds.width() >= bounds.height()) {
                    val projectedWidth = aspectRatio.ratioInverted(bounds.height())

                    // Scale to fit and calculate the difference
                    if (projectedWidth > bounds.width()) {
                        val scalar = bounds.width() / projectedWidth
                        yDiff = (bounds.height() - (bounds.height() * scalar))
                    } else {
                        xDiff = (bounds.width() - projectedWidth)
                    }
                } else {
                    val projectedHeight = aspectRatio.ratio(bounds.width())

                    // Scale to fit and calculate the difference
                    if (projectedHeight > bounds.height()) {
                        val scalar = bounds.height() / projectedHeight
                        xDiff = (bounds.width() - (bounds.width() * scalar))
                    } else {
                        yDiff = (bounds.height() - projectedHeight)
                    }
                }

                // Update the size of the base rectangle.
                base.left += xDiff / 2f
                base.right -= xDiff / 2f
                base.top += yDiff / 2f
                base.bottom -= yDiff / 2f
            }

            // Calculate the min dimens (quarter height/width or 64dp).
            val minDimens = min(min(bounds.height(), bounds.width()) / 4f, minDP)
            return Box(minDimens, base.left, base.top, base.right, base.bottom)
        }
    }
}
