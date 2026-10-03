package com.dev.alex.portfolio.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The design's Lucide-style stroke icons (portfolio-design/project/src/icons.jsx), 24×24,
 * 2px round strokes. Circles and rects from the SVG are written as arc paths. Drawn black
 * and coloured by Icon's tint.
 */
object FpIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Menu by lazy { icon("menu", "M4 6h16M4 12h16M4 18h16") }
    val X by lazy { icon("x", "M6 6l12 12M18 6 6 18") }
    val Plus by lazy { icon("plus", "M12 5v14M5 12h14") }
    val ChevDown by lazy { icon("chevDown", "m6 9 6 6 6-6") }
    val ChevUp by lazy { icon("chevUp", "m6 15 6-6 6 6") }
    val ChevRight by lazy { icon("chevRight", "m9 6 6 6-6 6") }
    val ChevLeft by lazy { icon("chevLeft", "m15 6-6 6 6 6") }
    val ArrowUp by lazy { icon("arrowUp", "M12 19V5M5 12l7-7 7 7") }
    val ArrowDown by lazy { icon("arrowDown", "M12 5v14M19 12l-7 7-7-7") }
    val Sun by lazy {
        icon(
            "sun",
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0",
            "M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41",
        )
    }
    val Moon by lazy { icon("moon", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z") }
    val Search by lazy { icon("search", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0", "m20 20-3.5-3.5") }
    val Filter by lazy { icon("filter", "M3 5h18l-7 9v6l-4-2v-4L3 5Z") }
    val Calendar by lazy {
        icon("calendar", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z", "M3 9h18M8 3v4M16 3v4")
    }
    val User by lazy { icon("user", "M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0", "M4 21a8 8 0 0 1 16 0") }
    val Lock by lazy {
        icon(
            "lock",
            "M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2Z",
            "M8 11V7a4 4 0 0 1 8 0v4",
        )
    }
    val Eye by lazy {
        icon("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
    }
    val Info by lazy { icon("info", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0", "M12 8v.01M12 12v4") }
    val Trending by lazy { icon("trending", "M3 17 9 11l4 4 8-8", "M15 7h6v6") }
    val Coins by lazy {
        icon(
            "coins",
            "M3 8a5 5 0 1 0 10 0a5 5 0 1 0-10 0",
            "M16 8a5 5 0 0 1 0 10 5 5 0 0 1-3-1",
            "M3 13a5 5 0 0 0 7 4.6",
        )
    }
    val Sparkle by lazy {
        icon("sparkle", "M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M5.6 18.4l2.8-2.8M15.6 8.4l2.8-2.8")
    }
    val Check by lazy { icon("check", "M4 12l5 5L20 6") }
    val Wallet by lazy {
        icon(
            "wallet",
            "M5 6h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2Z",
            "M16 13h3",
            "M3 10h18",
        )
    }
    val Target by lazy {
        icon(
            "target",
            "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0",
            "M7 12a5 5 0 1 0 10 0a5 5 0 1 0-10 0",
            "M10.5 12a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0",
        )
    }
    val Logout by lazy { icon("logout", "M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4", "M16 17l5-5-5-5M21 12H9") }
    val Folder by lazy {
        icon("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z")
    }
    val Home by lazy { icon("home", "M3 11 12 4l9 7", "M5 10v10h14V10") }
    val Rows by lazy {
        icon(
            "list",
            "M8 6h13M8 12h13M8 18h13",
            "M3 6a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
            "M3 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
            "M3 18a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
        )
    }
    val Pie by lazy { icon("pie", "M21 12a9 9 0 1 1-9-9v9Z", "M21 12a9 9 0 0 0-9-9") }
    val Ghost by lazy {
        icon(
            "ghost",
            "M9 10h.01M15 10h.01",
            "M12 2a7 7 0 0 0-7 7v12l3-2 2 2 2-2 2 2 2-2 3 2V9a7 7 0 0 0-7-7Z",
        )
    }
    val Globe by lazy {
        icon("globe", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0", "M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18")
    }
    val Refresh by lazy { icon("refresh", "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5") }
    val CloudOff by lazy {
        icon(
            "cloudOff",
            "m2 2 20 20",
            "M5.8 7.6A7 7 0 0 0 4 18h13",
            "M21.3 16.3A4.5 4.5 0 0 0 17.5 9h-1.8A7 7 0 0 0 9.6 5.1",
        )
    }
    val Fingerprint by lazy {
        icon(
            "fingerprint",
            "M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4",
            "M14 13.12c0 2.38 0 6.38-1 8.88",
            "M17.29 21.02c.12-.6.43-2.3.5-3.02",
            "M2 12a10 10 0 0 1 18-6",
            "M2 16h.01",
            "M21.8 16c.2-2 .131-5.354 0-6",
            "M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2",
            "M8.65 22c.21-.66.45-1.32.57-2",
            "M9 6.8a6 6 0 0 1 9 5.2v2",
        )
    }
}
