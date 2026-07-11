package com.fanta.androidsport.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val map_search: ImageVector
  get() {
    if (_map_search != null) {
      return _map_search!!
    }
    _map_search =
      ImageVector.Builder(
          name = "map_search",
          defaultWidth = 24.dp,
          defaultHeight = 24.dp,
          viewportWidth = 24f,
          viewportHeight = 24f,
        )
        .apply {
          path(
            fill = SolidColor(Color.White), // Use white tint by default
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 1f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
            strokeLineMiter = 1f,
            pathFillType = PathFillType.Companion.NonZero,
          ) {
            moveTo(16f, 10f)
            verticalLineTo(6.85f)
            verticalLineTo(10f)
            close()
            moveTo(4.35f, 20.7f)
            quadTo(3.85f, 20.9f, 3.43f, 20.59f)
            reflectiveQuadTo(3f, 19.75f)
            verticalLineToRelative(-14f)
            quadTo(3f, 5.43f, 3.19f, 5.18f)
            reflectiveQuadTo(3.7f, 4.8f)
            lineTo(9f, 3f)
            lineToRelative(6f, 2.1f)
            lineTo(19.65f, 3.3f)
            quadToRelative(0.5f, -0.2f, 0.93f, 0.11f)
            reflectiveQuadTo(21f, 4.25f)
            verticalLineToRelative(8.42f)
            quadTo(20.63f, 12.1f, 20.11f, 11.63f)
            reflectiveQuadTo(19f, 10.8f)
            verticalLineTo(5.7f)
            lineTo(16f, 6.85f)
            verticalLineTo(10f)
            quadToRelative(-0.52f, 0f, -1.02f, 0.09f)
            reflectiveQuadTo(14f, 10.35f)
            verticalLineTo(6.85f)
            lineTo(10f, 5.45f)
            verticalLineTo(18.52f)
            lineTo(4.35f, 20.7f)
            close()
            moveTo(5f, 18.3f)
            lineTo(8f, 17.15f)
            verticalLineTo(5.45f)
            lineToRelative(-3f, 1f)
            verticalLineTo(18.3f)
            close()
            moveTo(17.41f, 17.5f)
            quadTo(17.98f, 17f, 18f, 16f)
            quadToRelative(0.03f, -0.85f, -0.56f, -1.43f)
            reflectiveQuadTo(16f, 14f)
            reflectiveQuadToRelative(-1.42f, 0.57f)
            reflectiveQuadTo(14f, 16f)
            reflectiveQuadToRelative(0.58f, 1.43f)
            reflectiveQuadTo(16f, 18f)
            reflectiveQuadToRelative(1.41f, -0.5f)
            close()
            moveTo(16f, 20f)
            quadToRelative(-1.65f, 0f, -2.82f, -1.18f)
            reflectiveQuadTo(12f, 16f)
            reflectiveQuadToRelative(1.18f, -2.83f)
            reflectiveQuadTo(16f, 12f)
            reflectiveQuadToRelative(2.82f, 1.17f)
            reflectiveQuadTo(20f, 16f)
            quadToRelative(0f, 0.57f, -0.14f, 1.09f)
            reflectiveQuadToRelative(-0.41f, 0.96f)
            lineTo(22f, 20.6f)
            lineTo(20.6f, 22f)
            lineTo(18.05f, 19.45f)
            quadToRelative(-0.45f, 0.28f, -0.96f, 0.41f)
            reflectiveQuadTo(16f, 20f)
            close()
            moveTo(8f, 5.45f)
            verticalLineToRelative(11.7f)
            verticalLineTo(5.45f)
            close()
          }
        }
        .build()
    return _map_search!!
  }

private var _map_search: ImageVector? = null
