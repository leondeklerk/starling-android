package com.leondeklerk.starling.media

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView

/**
 * [SurfaceView] that resizes based on the given video dimensions.
 */
class VideoSurfaceView(context: Context?, attrs: AttributeSet?) : SurfaceView(context, attrs) {

    private var videoWidth = 0f
    private var videoHeight = 0f

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val wSize = MeasureSpec.getSize(wSpec)
        val hSize = MeasureSpec.getSize(hSpec)
        var wResult = wSize
        var hResult = hSize

        if (videoWidth > 0 && videoHeight > 0) {
            val videoRatio = videoHeight / videoWidth

            hResult = (wResult * videoRatio).toInt()

            if (hResult > hSize) {
                hResult = hSize
                wResult = (hResult / videoRatio).toInt()
            }
        }
        setMeasuredDimension(wResult, hResult)
    }

    /**
     * Set the size of the surface based on the given with and height.
     * Will request a new layout of the view.
     */
    fun setVideoSize(width: Float, height: Float) {
        videoWidth = width
        videoHeight = height
        requestLayout()
    }
}
