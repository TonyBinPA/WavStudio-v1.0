package com.wavstudio.app.export

/** Works out the output frame size for a video export. Pure logic so it can be unit tested. */
object OutputGeometry {
    private const val FALLBACK_WIDTH = 1920
    private const val FALLBACK_HEIGHT = 1080

    /**
     * @param sourceWidth display width of the first video clip (after rotation)
     * @param sourceHeight display height of the first video clip (after rotation)
     * @return even (width, height) whose short side matches [resolution] (never upscaling for
     *   [Resolution.ORIGINAL]) and whose aspect ratio matches the source.
     */
    fun outputSize(sourceWidth: Int, sourceHeight: Int, resolution: Resolution): Pair<Int, Int> {
        val w = if (sourceWidth > 0) sourceWidth else FALLBACK_WIDTH
        val h = if (sourceHeight > 0) sourceHeight else FALLBACK_HEIGHT
        val target = resolution.shortSide ?: return even(w) to even(h)
        val shortSide = minOf(w, h)
        val scale = target.toDouble() / shortSide
        return even((w * scale).toInt()) to even((h * scale).toInt())
    }

    private fun even(v: Int): Int = (v.coerceAtLeast(2) / 2) * 2
}
