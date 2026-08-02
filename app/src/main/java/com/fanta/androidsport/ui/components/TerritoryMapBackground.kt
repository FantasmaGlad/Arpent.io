package com.fanta.androidsport.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fanta.androidsport.utils.closeRing
import com.fanta.androidsport.utils.getPolygonArea
import com.fanta.androidsport.utils.getPolygonCentroid
import com.fanta.androidsport.utils.groupRingsIntoPolygons
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotationState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.gestures.gestures

// Non-interactive Mapbox map (same style as the "Carte" tab, without any HUD button).
// Used as a decorative background behind the profile and the courses feed.
// Camera centers on the largest territory polygon, or on fallbackCenter when there is none.
@Composable
fun TerritoryMapBackground(
    polygons: List<List<Point>>,
    empireColor: Color,
    fallbackCenter: Point? = null,
    modifier: Modifier = Modifier
) {
    // Rings can include holes carved out by an opponent's loop (see
    // groupRingsIntoPolygons doc in GeoUtils.kt) — group them into
    // [exterior, hole...] sets so a hole renders as a gap, not a solid patch,
    // and so it never gets mistaken for the "largest" territory below.
    val polygonGroups = remember(polygons) {
        groupRingsIntoPolygons(polygons.map { closeRing(it) })
    }
    val largestGroup = remember(polygonGroups) {
        polygonGroups.maxByOrNull { getPolygonArea(it[0]) }
    }
    val centroid = remember(largestGroup, fallbackCenter) {
        largestGroup?.let { getPolygonCentroid(it[0]) }
            ?: fallbackCenter
            ?: Point.fromLngLat(2.3522, 48.8566)
    }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(centroid)
            zoom(if (largestGroup != null) 13.0 else 11.5)
            pitch(35.0)
            bearing(15.0)
        }
    }

    // Recenter when territories or the fallback location arrive asynchronously
    androidx.compose.runtime.LaunchedEffect(centroid) {
        mapViewportState.setCameraOptions {
            center(centroid)
            zoom(if (largestGroup != null) 13.0 else 11.5)
            pitch(35.0)
            bearing(15.0)
        }
    }

    MapboxMap(
        modifier = modifier,
        mapViewportState = mapViewportState,
        logo = {},
        attribution = {},
        scaleBar = {},
        compass = {}
    ) {
        MapStyle(style = "mapbox://styles/fantasmaglad/cmqe0myj4002c01qr2jd549n8")
        MapEffect(Unit) { mapView ->
            mapView.gestures.scrollEnabled = false
            mapView.gestures.pinchToZoomEnabled = false
            mapView.gestures.doubleTapToZoomInEnabled = false
            mapView.gestures.doubleTouchToZoomOutEnabled = false
            mapView.gestures.rotateEnabled = false
            mapView.gestures.pitchEnabled = false
        }
        polygonGroups.forEach { group ->
            val polygonState = remember(group, empireColor) {
                PolygonAnnotationState().apply {
                    fillColor = empireColor.copy(alpha = 0.35f)
                    fillOutlineColor = empireColor
                }
            }
            PolygonAnnotation(
                points = group,
                polygonAnnotationState = polygonState
            )
        }
    }
}
