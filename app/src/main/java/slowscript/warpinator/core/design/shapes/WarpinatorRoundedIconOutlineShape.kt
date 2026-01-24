package slowscript.warpinator.core.design.shapes

import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon

private const val ICON_CORNER_RADIUS = 8f
private const val ICON_CENTER_X = 0.5f
private const val ICON_CENTER_Y = 0.5f

val WarpinatorRoundedIconOutlineShape by lazy {
    val vertices = floatArrayOf(
        // x,      y
        0.409f, 0.623f,
        0.011f, 0.689f,
        0.000f, 0.531f,
        0.363f, 0.506f,
        0.415f, 0.414f,
        0.202f, 0.148f,
        0.334f, 0.060f,
        0.494f, 0.350f,
        0.629f, 0.354f,
        0.775f, 0.000f,
        0.997f, 0.100f,
        0.741f, 0.453f,
        0.734f, 0.638f,
        1.000f, 0.815f,
        0.875f, 1.000f,
        0.663f, 0.704f,
        0.532f, 0.738f,
        0.457f, 0.957f,
        0.330f, 0.887f,
        0.442f, 0.692f,
    )

    RoundedPolygon(
        vertices = vertices,
        rounding = CornerRounding(radius = ICON_CORNER_RADIUS),
        centerX = ICON_CENTER_X,
        centerY = ICON_CENTER_Y,
    )
}
