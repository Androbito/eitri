package app.servante.core.write

import app.servante.core.ToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behaviours:
 * - a write request with no changes is rejected, because the dialog would have nothing to list
 * - a write request needs a title, which is the question the dialog asks
 * - the total size of a trash request is the sum of its items, which the dialog shows
 */
class WriteRequestTest {
    @Test
    fun `a request with no changes is rejected`() {
        val thrown = runCatching {
            WriteRequest(ToolId.SdkDisk, "Move items to the trash?", "Frees space.", emptyList())
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `a request needs a title`() {
        val thrown = runCatching {
            WriteRequest(
                tool = ToolId.Gradle,
                title = "",
                explanation = "One file changes.",
                changes = listOf(PlannedChange.EditFile("gradle/libs.versions.toml", "diff", "ksp")),
            )
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `the total size is the sum of the items to be trashed`() {
        val request = WriteRequest(
            tool = ToolId.SdkDisk,
            title = "Move 2 items to the trash?",
            explanation = "This frees space.",
            changes = listOf(
                PlannedChange.MoveToTrash("system-images/android-33", sizeBytes = 6_200_000_000),
                PlannedChange.MoveToTrash("ndk/25.2.9519653", sizeBytes = 4_100_000_000),
                PlannedChange.EditFile("gradle/libs.versions.toml", "diff", "unrelated"),
            ),
        )

        assertEquals(10_300_000_000, request.totalSizeBytes)
    }
}
