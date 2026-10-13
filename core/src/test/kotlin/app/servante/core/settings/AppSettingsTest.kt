package app.servante.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Behaviours:
 * - the defaults follow the system theme and language
 * - the update check is off by default, because it is a network call
 * - a known language tag maps to the language, including a regional variant
 * - an unknown language tag falls back to English rather than showing keys
 */
class AppSettingsTest {
    @Test
    fun `the defaults follow the system and make no network calls`() {
        val defaults = AppSettings.DEFAULTS

        assertEquals(Appearance.FollowSystem, defaults.appearance)
        assertNull(defaults.language)
        assertFalse(defaults.checkForUpdatesAtLaunch)
        assertNull(defaults.androidSdkPath)
    }

    @Test
    fun `a known language tag maps to the language`() {
        assertEquals(Language.French, Language.forTag("fr"))
        assertEquals(Language.French, Language.forTag("fr-FR"))
        assertEquals(Language.English, Language.forTag("en-GB"))
    }

    @Test
    fun `an unknown language falls back to English`() {
        assertEquals(Language.English, Language.forTag("ar"))
        assertEquals(Language.English, Language.forTag(null))
    }
}
