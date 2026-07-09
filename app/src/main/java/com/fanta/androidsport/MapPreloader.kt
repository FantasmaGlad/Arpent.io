package com.fanta.androidsport

import android.content.Context
import android.util.Log
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style

/**
 * Singleton that pre-warms the Mapbox map engine during the splash/loading screen
 * so that when ConquestMapScreen is first composed, the style and tiles are already
 * cached and the map appears almost instantly.
 *
 * Call [warmUp] once from the loading phase. The created MapView is immediately
 * destroyed after the style finishes loading – the only purpose is to force
 * Mapbox to download and cache the style JSON + initial vector tiles so that the
 * real MapView in ConquestMapScreen benefits from the warm disk/memory cache.
 */
object MapPreloader {

    private const val TAG = "MapPreloader"
    private const val CUSTOM_STYLE = "mapbox://styles/fantasmaglad/cmqe0myj4002c01qr2jd549n8"

    @Volatile
    private var hasWarmedUp = false

    /**
     * Kick-starts a headless MapView that loads the custom Mapbox style.
     * This forces the SDK to download and cache style JSON, glyphs, sprites and
     * initial vector tiles.  Must be called on the **main thread** (MapView requires it).
     */
    fun warmUp(context: Context) {
        if (hasWarmedUp) return
        hasWarmedUp = true

        try {
            val mapView = MapView(context, MapInitOptions(context))
            mapView.mapboxMap.loadStyle(CUSTOM_STYLE) { _: Style ->
                Log.d(TAG, "Map style pre-loaded successfully – destroying warm-up MapView")
                mapView.onStop()
                mapView.onDestroy()
            }
            // Start the map lifecycle so it actually begins loading
            mapView.onStart()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pre-warm Mapbox map", e)
        }
    }
}
