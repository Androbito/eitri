package app.servante.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing steps from `design/tokens.md`. Only these values are used for gaps and padding, so a
 * layout cannot drift to a 13 or a 22 that matches nothing else on the screen.
 */
@Immutable
public object Spacing {
    public val xxs: Dp = 4.dp
    public val xs: Dp = 8.dp
    public val s: Dp = 12.dp
    public val m: Dp = 16.dp
    public val l: Dp = 20.dp
    public val xl: Dp = 28.dp
    public val xxl: Dp = 32.dp
}

/** Corner radii: 10 on panels and cards, 6 on controls, 4 on chips. */
@Immutable
public object Radius {
    public val panel: Dp = 10.dp
    public val control: Dp = 6.dp
    public val chip: Dp = 4.dp
}

/** Fixed sizes the designs specify. */
@Immutable
public object Sizes {
    public val borderWidth: Dp = 1.dp

    public val controlHeight: Dp = 36.dp
    public val controlHeightSmall: Dp = 32.dp
    public val controlHeightTiny: Dp = 28.dp

    /** The one main action of a sheet, per `design/tokens.md`. */
    public val controlHeightSheetAction: Dp = 40.dp

    public val sidebarWidth: Dp = 232.dp
    public val inspectorMinWidth: Dp = 280.dp
    public val inspectorMaxWidth: Dp = 360.dp

    public val mainPaddingTop: Dp = 28.dp
    public val mainPaddingHorizontal: Dp = 32.dp
    public val mainPaddingBottom: Dp = 36.dp

    public val statusDot: Dp = 8.dp
    public val progressBarHeight: Dp = 4.dp

    /**
     * The smallest a thing the user clicks may be. The designs draw 28 to 36 px controls; this is the
     * floor the accessibility rules in `mobile-quality-audit` ask for, applied to Servante itself, so a
     * visually small control still has a large enough target.
     */
    public val minimumTouchTarget: Dp = 44.dp
}
