package com.meerkly.android.ui.nav

/**
 * The entire adaptive decision, as a pure function of the container's width.
 *
 * Deliberately not `material3-window-size-class`: its
 * `calculateWindowSizeClass()` requires an Activity, which would make these
 * branches untestable on the JVM — the thing this codebase optimises for. The
 * breakpoints are Material's own (600 / 840dp), just inlined.
 *
 * Width comes from the measured container, not the physical screen, so
 * split-screen and freeform windows are correct for free.
 */
enum class WindowWidth {
    Compact,
    Medium,
    Expanded,
    ;

    /**
     * List and detail side by side. Expanded only: at Medium (600–839dp) two
     * panes would be roughly 300dp each, which is worse than one good column.
     */
    fun twoPane(destination: Destination): Boolean =
        this == Expanded &&
            (destination == Destination.Activity || destination == Destination.Devices)

    /** Bottom bar on phones, side rail from 600dp up. */
    val usesRail: Boolean get() = this != Compact

    /** Cap for the centred content column; wider layouts get a little more room. */
    val contentMaxWidthDp: Int get() = if (this == Expanded) 720 else 560

    companion object {
        fun of(widthDp: Float): WindowWidth = when {
            widthDp < 600f -> Compact
            widthDp < 840f -> Medium
            else -> Expanded
        }
    }
}
