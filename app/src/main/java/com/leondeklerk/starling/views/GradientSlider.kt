package com.leondeklerk.starling.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import com.google.android.material.slider.Slider
import com.leondeklerk.starling.BuildConfig
import com.leondeklerk.starling.R

/**
 * Special type of [Slider] that allows a [GradientDrawable] to be set as track background.
 * Automatically sets the track color of the default component to transparent.
 */
class GradientSlider(context: Context, attributeSet: AttributeSet?) : Slider(context, attributeSet) {

    var trackGradient: GradientDrawable? = null
        set(value) {
            field = value
            setGradientProperties()
            invalidate()
        }

    /**
     * Responsible for initializing the component:
     * - Setting the old track color to transparent
     * - Retrieving the gradient from attributes (if set)
     * - Updating the gradient or creating a sample gradient if in debug mode.
     */
    init {
        // Hide the standard track
        trackActiveTintList = resources.getColorStateList(R.color.transparent_slider_track_color, context.theme)
        trackInactiveTintList = resources.getColorStateList(R.color.transparent_slider_track_color, context.theme)

        val a = context.obtainStyledAttributes(attributeSet, R.styleable.GradientSlider, 0, 0)
        val gradient = a.getDrawable(R.styleable.GradientSlider_trackGradient)

        // Set the gradient from XML
        trackGradient = when {
            gradient is GradientDrawable -> {
                gradient
            }
            gradient != null -> {
                throw IllegalArgumentException("Drawable is not a GradientDrawable")
            }
            else -> {
                // Tools gradient for design during development
                if (BuildConfig.DEBUG) {
                    // Build an example hue drawable
                    val hues = IntArray(360)
                    for (i in 0 until 360) {
                        hues[i] = Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
                    }
                    GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        hues
                    )
                } else {
                    null
                }
            }
        }
        a.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        if (isEnabled) {
            val top = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)

            // Draw the new gradient track
            canvas.save()
            canvas.translate(trackSidePadding.toFloat() - trackHeight / 2f, top - trackHeight / 2f)
            trackGradient?.draw(canvas)
            canvas.restore()
        } else {
            val top = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, resources.displayMetrics)

            // Draw the new gradient track
            canvas.save()
            canvas.translate(trackSidePadding.toFloat() - trackHeight / 2f, top - trackHeight / 2f)
            getDisabledGradient().draw(canvas)
            canvas.restore()
        }

        // Draw the default components over the new track
        super.onDraw(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        setGradientProperties()
    }

    /**
     * Updates the gradient by adding rounded corners and setting the bounds.
     */
    private fun setGradientProperties(gradient: GradientDrawable? = trackGradient) {
        // Set the gradient properties
        gradient?.cornerRadius = trackHeight / 2f
        // width must include the sizes of the rounded caps a stroke has.
        gradient?.setBounds(0, 0, trackWidth + trackHeight, trackHeight)
    }

    /**
     * Get a gradient that represents the disables state
     * @return a drawable containing a grayed out track.
     */
    private fun getDisabledGradient(): GradientDrawable {
        // Create just a gray gradient.
        val disabledGray = resources.getColor(R.color.slider_inactive_gray, context.theme)
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(disabledGray, disabledGray)
        )
        setGradientProperties(gradient)
        return gradient
    }
}
