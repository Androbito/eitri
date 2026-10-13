package app.servante.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import app.servante.core.settings.Appearance
import app.servante.core.settings.Language
import app.servante.ui.text.LocalStrings
import app.servante.ui.text.ServanteStrings
import app.servante.ui.text.stringsFor

private val LocalServanteColors: ProvidableCompositionLocal<ServanteColors> =
    staticCompositionLocalOf { ServanteColors.Light }

private val LocalServanteTypography: ProvidableCompositionLocal<ServanteTypography> =
    staticCompositionLocalOf { ServanteTypography() }

/**
 * The theme every Servante screen sits inside.
 *
 * Material 3's own theme is deliberately not used: Servante's design system is its own, and mixing the
 * two means two sources of truth for every colour. Material components that are used take their colours
 * from these tokens explicitly.
 */
public object ServanteTheme {
    public val colors: ServanteColors
        @Composable @ReadOnlyComposable
        get() = LocalServanteColors.current

    public val typography: ServanteTypography
        @Composable @ReadOnlyComposable
        get() = LocalServanteTypography.current

    public val strings: ServanteStrings
        @Composable @ReadOnlyComposable
        get() = LocalStrings.current
}

/**
 * @param appearance follows the system by default, as the designs specify.
 * @param language null means follow the system language, falling back to English.
 */
@Composable
public fun ServanteTheme(
    appearance: Appearance = Appearance.FollowSystem,
    language: Language? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        Appearance.FollowSystem -> isSystemInDarkTheme()
        Appearance.Light -> false
        Appearance.Dark -> true
    }
    val colors = if (dark) ServanteColors.Dark else ServanteColors.Light
    val typography = ServanteTypography()

    CompositionLocalProvider(
        LocalServanteColors provides colors,
        LocalServanteTypography provides typography,
        LocalStrings provides stringsFor(language),
        LocalContentColor provides colors.text,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = colors.accentInk,
            backgroundColor = colors.accentSoft,
        ),
        content = content,
    )
}

/** The body style with the primary text colour already applied, for the common case. */
public val ServanteTheme.bodyTextStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = typography.body.copy(color = colors.text)
