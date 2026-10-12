package app.servante.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Servante's colours, by the role each one plays.
 *
 * Translated one to one from the table in `design/tokens.md`, which is the source of truth. They are
 * named by role and never by hue, because `accent` and `ok` are both green and a component that asked
 * for "green" would pick the wrong one. Nothing in the app writes a hex value: if a colour is needed
 * that is not here, it belongs here first.
 *
 * Two rules from the designs are enforced by what this type offers rather than by review:
 * [accent] is a fill colour only, too faint for text or a thin line on a light background, so text and
 * borders use [accentInk]; and status is never shown by colour alone, which is why the status components
 * in `component/` always draw a dot beside a word.
 */
@Immutable
public data class ServanteColors(
    public val background: Color,
    public val surface: Color,
    public val raised: Color,
    public val line: Color,
    public val text: Color,
    public val muted: Color,
    public val accent: Color,
    public val onAccent: Color,
    public val accentInk: Color,
    public val accentSoft: Color,
    public val ok: Color,
    public val okSoft: Color,
    public val warn: Color,
    public val warnSoft: Color,
    public val critical: Color,
    public val criticalSoft: Color,
    public val diffAdded: Color,
    public val diffRemoved: Color,
    public val isLight: Boolean,
) {
    /** Chart series, in the order `design/tokens.md` lists them. */
    public val chartSeries: List<Color> = listOf(
        Color(0xFF0A7D43),
        Color(0xFF3DDC84),
        Color(0xFF2F6FDE),
        Color(0xFFE08A1E),
        Color(0xFF8A938E),
    )

    public companion object {
        public val Light: ServanteColors = ServanteColors(
            background = Color(0xFFF5F7F5),
            surface = Color(0xFFFFFFFF),
            raised = Color(0xFFE9EEEA),
            line = Color(0xFFD3DAD5),
            text = Color(0xFF101412),
            muted = Color(0xFF55605A),
            accent = Color(0xFF3DDC84),
            onAccent = Color(0xFF06210F),
            accentInk = Color(0xFF0A7D43),
            accentSoft = Color(0xFFDDF7E8),
            ok = Color(0xFF137A43),
            okSoft = Color(0xFFDDF3E6),
            warn = Color(0xFF8F5B00),
            warnSoft = Color(0xFFFBEBCB),
            critical = Color(0xFFC6292F),
            criticalSoft = Color(0xFFFBE0E1),
            diffAdded = Color(0xFFE3F4E9),
            diffRemoved = Color(0xFFFBE4E5),
            isLight = true,
        )

        public val Dark: ServanteColors = ServanteColors(
            background = Color(0xFF0E1210),
            surface = Color(0xFF151A17),
            raised = Color(0xFF1E2521),
            line = Color(0xFF303A34),
            text = Color(0xFFECF1EE),
            muted = Color(0xFFA3AEA8),
            accent = Color(0xFF3DDC84),
            onAccent = Color(0xFF06210F),
            accentInk = Color(0xFF3DDC84),
            accentSoft = Color(0xFF153524),
            ok = Color(0xFF4FD08A),
            okSoft = Color(0xFF173A2B),
            warn = Color(0xFFF2B453),
            warnSoft = Color(0xFF3D2F14),
            critical = Color(0xFFFF7A7F),
            criticalSoft = Color(0xFF44191D),
            diffAdded = Color(0xFF17352A),
            diffRemoved = Color(0xFF3E1A1E),
            isLight = false,
        )
    }
}
