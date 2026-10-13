package app.servante.storage.project

import app.servante.core.project.CatalogPlugin
import app.servante.core.project.ModuleKind
import app.servante.core.project.VersionCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviours:
 * - a plugin applied by id in the Kotlin DSL is found
 * - a plugin applied by id in Groovy is found
 * - a plugin applied through a version catalog alias resolves to its id
 * - a module applying com.android.application is an application module
 * - a module applying com.android.library is an Android library
 * - a module applying only the Kotlin JVM plugin is a JVM module
 * - a module whose plugins cannot be read is Unknown rather than assumed to be a library
 * - an alias that the catalog does not define leaves the plugin unresolved instead of inventing an id
 */
class BuildScriptPluginsTest {
    private val catalog = VersionCatalog(
        path = "gradle/libs.versions.toml",
        versions = mapOf("agp" to "8.14.0"),
        libraries = emptyList(),
        plugins = listOf(
            CatalogPlugin("android-application", "com.android.application", "8.14.0"),
            CatalogPlugin("kotlin-android", "org.jetbrains.kotlin.android", "2.2.20"),
        ),
    )

    @Test
    fun `finds a plugin applied by id in the Kotlin DSL`() {
        val ids = BuildScriptPlugins.appliedPluginIds(
            """
            plugins {
                id("com.android.library")
                kotlin("jvm")
            }
            """.trimIndent(),
            catalog = null,
        )

        assertEquals(setOf("com.android.library", "org.jetbrains.kotlin.jvm"), ids)
    }

    @Test
    fun `finds a plugin applied by id in Groovy`() {
        val ids = BuildScriptPlugins.appliedPluginIds(
            """
            plugins {
                id 'com.android.application'
                id 'org.jetbrains.kotlin.android'
            }
            """.trimIndent(),
            catalog = null,
        )

        assertEquals(setOf("com.android.application", "org.jetbrains.kotlin.android"), ids)
    }

    @Test
    fun `resolves a plugin applied through a catalog alias`() {
        val ids = BuildScriptPlugins.appliedPluginIds(
            """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.android)
            }
            """.trimIndent(),
            catalog = catalog,
        )

        assertEquals(setOf("com.android.application", "org.jetbrains.kotlin.android"), ids)
    }

    @Test
    fun `leaves an alias the catalog does not define unresolved`() {
        val ids = BuildScriptPlugins.appliedPluginIds(
            """
            plugins {
                alias(libs.plugins.ksp)
            }
            """.trimIndent(),
            catalog = catalog,
        )

        assertEquals(emptySet(), ids)
    }

    @Test
    fun `classifies module kinds from the plugins applied`() {
        assertEquals(ModuleKind.Application, BuildScriptPlugins.kindOf(setOf("com.android.application")))
        assertEquals(ModuleKind.AndroidLibrary, BuildScriptPlugins.kindOf(setOf("com.android.library")))
        assertEquals(ModuleKind.Jvm, BuildScriptPlugins.kindOf(setOf("org.jetbrains.kotlin.jvm")))
        assertEquals(ModuleKind.Unknown, BuildScriptPlugins.kindOf(emptySet()))
        assertEquals(ModuleKind.Unknown, BuildScriptPlugins.kindOf(setOf("fjord.convention.android")))
    }

    @Test
    fun `an application module is still an application when other plugins are applied too`() {
        val kind = BuildScriptPlugins.kindOf(
            setOf("com.android.application", "org.jetbrains.kotlin.android", "com.google.devtools.ksp"),
        )

        assertEquals(ModuleKind.Application, kind)
    }
}
