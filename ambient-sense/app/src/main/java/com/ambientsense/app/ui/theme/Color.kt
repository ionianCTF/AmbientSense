package com.ambientsense.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A palette in the spirit of anthropic.com: warm off-white paper, a single clay/coral
 * accent, muted secondary tones and near-black ink. Dark mode is the same set inverted
 * onto Anthropic's dark slate.
 */
object AmbientPalette {

    // ---- paper & ink ----
    val Ivory = Color(0xFFF0EEE6)
    val IvoryDeep = Color(0xFFE8E5DB)
    val Paper = Color(0xFFFAF9F5)
    val Cloud = Color(0xFFE3DACC)
    val Manilla = Color(0xFFEBDBBC)
    val Kraft = Color(0xFFD4A27F)
    val BookCloth = Color(0xFFCC785C)
    val Clay = Color(0xFFD97757)
    val ClayDeep = Color(0xFFBF5F3F)

    val SlateDark = Color(0xFF191919)
    val SlateMid = Color(0xFF5F5E5A)
    val SlateLight = Color(0xFF91918D)

    val Olive = Color(0xFF788C5D)
    val OliveBright = Color(0xFF8CA46B)
    val Stone = Color(0xFF6A8CAF)
    val StoneDeep = Color(0xFF4F6B87)

    // ---- dark mode ----
    val Ink = Color(0xFF141413)
    val InkSurface = Color(0xFF1F1E1D)
    val InkRaised = Color(0xFF262523)
    val InkBorder = Color(0xFF35332F)
    val InkText = Color(0xFFF0EEE6)
    val InkTextDim = Color(0xFFA8A39A)

    // ---- data visualisation (stable across themes) ----
    val PeopleColor = Color(0xFFCC785C)
    val VehicleColor = Color(0xFF5B7C99)
    val StaticColor = Color(0xFF91918D)
    val NoiseColor = Color(0xFF7C8F5A)
    val NetworkColor = Color(0xFF6A8CAF)
    val DangerColor = Color(0xFFB4544A)
    val GoodColor = Color(0xFF6E8B5A)

    // ---- signal quality ramp (weak -> strong) ----
    val SignalRamp = listOf(
        Color(0xFFB4544A),
        Color(0xFFCC8C5C),
        Color(0xFFD4B25F),
        Color(0xFF8CA46B),
        Color(0xFF6E8B5A)
    )
}

/** Semantic "motion class" colour used by the device list and the map. */
fun motionColor(motion: com.ambientsense.app.model.MotionClass): Color = when (motion) {
    com.ambientsense.app.model.MotionClass.VEHICLE -> AmbientPalette.VehicleColor
    com.ambientsense.app.model.MotionClass.PEDESTRIAN -> AmbientPalette.PeopleColor
    com.ambientsense.app.model.MotionClass.STATIC -> AmbientPalette.StaticColor
    com.ambientsense.app.model.MotionClass.UNKNOWN -> AmbientPalette.SlateLight
}
