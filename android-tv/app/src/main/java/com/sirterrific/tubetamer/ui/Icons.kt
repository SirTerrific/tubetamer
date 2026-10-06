package com.sirterrific.tubetamer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons in the web app's style: 16x16 grid, 1.5 stroke, round caps and joins
 * (same drawings as the header and menu icons in web/templates/base.html where
 * one exists). Tinted by the surrounding content color.
 */
object TtIcons {
    private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 16f, 16f).apply {
            strokes.forEach { d ->
                addPath(
                    addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
            fills.forEach { d -> addPath(addPathNodes(d), fill = SolidColor(Color.Black)) }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    val Search = icon("search", circle(7f, 7f, 4.5f), "M10.5 10.5L14 14")
    val Mic = icon("mic", "M6 3.5a2 2 0 0 1 4 0v4a2 2 0 0 1 -4 0z", "M3.5 7.5a4.5 4.5 0 0 0 9 0", "M8 12v2.5")
    /** Web: avatar menu "Requests". */
    val Requests = icon("requests", "M8 1v6m0 0l-2.5-2.5M8 7l2.5-2.5", "M3.5 9h9a1.5 1.5 0 0 1 1.5 1.5v2a1.5 1.5 0 0 1 -1.5 1.5h-9a1.5 1.5 0 0 1 -1.5 -1.5v-2a1.5 1.5 0 0 1 1.5 -1.5z")
    /** Web: header "History". */
    val Clock = icon("clock", circle(8f, 8f, 6f), "M8 5v3.5l2.5 1.5")
    /** Web: avatar menu "Switch profile". */
    val SwitchProfile = icon("switch", circle(6f, 6f, 3f), "M11 14c0-2.2-2.2-4-5-4s-5 1.8-5 4", "M11 5l2 2 2-2")
    val Back = icon("back", "M10 3L5 8l5 5")
    val Play = icon("play", fills = listOf("M5 3.2v9.6a.6.6 0 0 0 .9.5l7.6-4.8a.6.6 0 0 0 0-1l-7.6-4.8a.6.6 0 0 0 -.9.5z"))
    val Retry = icon("retry", "M13 8a5 5 0 1 1 -1.5-3.6", "M13 2.5v2.5h-2.5")
    val Check = icon("check", "M3 8.5l3.2 3L13 4.5")
    val Hourglass = icon("hourglass", "M4 2h8M4 14h8", "M5 2c0 3 6 3 6 6s-6 3-6 6", "M11 2c0 3-6 3-6 6s6 3 6 6")
    val Blocked = icon("blocked", circle(8f, 8f, 6f), "M3.8 12.2l8.4-8.4")
    val Moon = icon("moon", "M13 9.5A5.5 5.5 0 1 1 6.5 3a4.5 4.5 0 0 0 6.5 6.5z")
    val Server = icon("server", "M3 2.5h10a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-10a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z", "M3 8.5h10a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-10a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1z", "M4.5 5h.01M4.5 11h.01")
    val Delete = icon("delete", "M6 3.5h7a1 1 0 0 1 1 1v7a1 1 0 0 1 -1 1h-7l-4-4.5z", "M8 6.5l3 3M11 6.5l-3 3")
    val Book = icon("book", "M8 4c-1.5-1-3.5-1.5-6-1.5v10c2.5 0 4.5.5 6 1.5c1.5-1 3.5-1.5 6-1.5v-10c-2.5 0-4.5.5-6 1.5z", "M8 4v10")
    val Star = icon("star", "M8 1.8l1.9 3.9 4.3.6-3.1 3 .7 4.3L8 11.6l-3.8 2 .7-4.3-3.1-3 4.3-.6z")
    val Bolt = icon("bolt", "M9 1.5L3.5 9H8l-1 5.5L12.5 7H8z")
    val Channel = icon("channel", "M3 3.5h10a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1 -1.5 1.5h-10a1.5 1.5 0 0 1 -1.5 -1.5v-6a1.5 1.5 0 0 1 1.5 -1.5z", "M6.5 6v4l3.5-2z")
    val Wifi = icon("wifi", "M1.5 6a9.5 9.5 0 0 1 13 0", "M4 8.6a6 6 0 0 1 8 0", "M6.4 11a2.6 2.6 0 0 1 3.2 0", "M8 13.5h.01")
}
