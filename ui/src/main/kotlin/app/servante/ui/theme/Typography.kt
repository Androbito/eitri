package app.servante.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The three faces from `design/tokens.md`, bundled with the app.
 *
 * They are variable fonts, which is how Google Fonts publishes all three, so each weight is one axis
 * setting on one file rather than a separate file. Bundling rather than relying on the system means the
 * app looks the same on a machine that has none of them installed, which is most machines.
 *
 * Licences are beside the files: all three are under the SIL Open Font License.
 */
public object ServanteFonts {
    /**
     * One weight of a variable font.
     *
     * The weight is passed twice on purpose: once so the font resolver knows which weight this entry
     * answers for, and once as the `wght` axis setting, which is what actually makes the file render at
     * that weight. Without the axis setting every entry would render at the file's default instance,
     * so bold text would come out regular.
     *
     * The factory is the one in `androidx.compose.ui.text.platform`, which loads from the classpath.
     * The similarly named one in `androidx.compose.ui.text.font` takes an Android resource id and does
     * not apply here. The path is passed positionally because the two name that parameter differently,
     * so this file does not depend on which is in scope.
     */
    private fun variableFont(path: String, weight: FontWeight) = Font(
        path,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    private fun bricolage(weight: FontWeight) = variableFont("fonts/BricolageGrotesque-Variable.ttf", weight)

    private fun instrument(weight: FontWeight) = variableFont("fonts/InstrumentSans-Variable.ttf", weight)

    private fun jetBrainsMono(weight: FontWeight) = variableFont("fonts/JetBrainsMono-Variable.ttf", weight)

    /** Display, titles and big numbers. */
    public val display: FontFamily = FontFamily(
        bricolage(FontWeight.Medium),
        bricolage(FontWeight.Bold),
    )

    /** Everything that is read as prose. */
    public val body: FontFamily = FontFamily(
        instrument(FontWeight.Normal),
        instrument(FontWeight.Medium),
        instrument(FontWeight.SemiBold),
    )

    /**
     * Fingerprints, versions, paths and code. Everything in this face is selectable and has a copy
     * action, because the reason a value is monospaced here is that the user has to paste it somewhere.
     */
    public val mono: FontFamily = FontFamily(
        jetBrainsMono(FontWeight.Normal),
        jetBrainsMono(FontWeight.Medium),
    )
}

/**
 * The type scale from `design/tokens.md`, by role.
 *
 * Sizes are in `sp` so they follow the system text scale. The designs give pixel values at 1x, which map
 * to `sp` directly.
 */
@Immutable
public data class ServanteTypography(
    public val display: TextStyle = TextStyle(
        fontFamily = ServanteFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.02).em,
    ),
    public val screenTitle: TextStyle = TextStyle(
        fontFamily = ServanteFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.01).em,
    ),
    public val panelTitle: TextStyle = TextStyle(
        fontFamily = ServanteFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    ),
    /** The verdict line at the top of a tool screen, and a dialog's question. */
    public val verdict: TextStyle = TextStyle(
        fontFamily = ServanteFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
    public val bigNumber: TextStyle = TextStyle(
        fontFamily = ServanteFonts.display,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
    ),
    public val label: TextStyle = TextStyle(
        fontFamily = ServanteFonts.body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    public val body: TextStyle = TextStyle(
        fontFamily = ServanteFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    public val secondary: TextStyle = TextStyle(
        fontFamily = ServanteFonts.body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    public val data: TextStyle = TextStyle(
        fontFamily = ServanteFonts.mono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    /** A standard chip: `WCAG 1.4.3 Contrast`, `MASVS-STORAGE-1`, `CWE-798`. */
    public val chip: TextStyle = TextStyle(
        fontFamily = ServanteFonts.mono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    ),
)
