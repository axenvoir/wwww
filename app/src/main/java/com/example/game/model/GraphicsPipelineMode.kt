package com.example.game.model

enum class GraphicsPipelineMode(
    val displayName: String,
    val subtitle: String,
) {
    CLASSIC("Classic (Stable)", "Proven baseline OpenGL ES 3.0 renderer"),
    REALISTIC_PBR("Realistic PBR (Beta)", "Enhanced lighting, shadows & post-FX with auto-fallback"),
}
