package app.servante.core.project

import app.servante.core.Availability

/**
 * Reads a folder and says what project is in it.
 *
 * Declared here so tools and screens depend on the question, not on the file system. The implementation
 * lives in `storage`, which is the module allowed to touch the disk. See
 * `docs/adr/0009-read-the-user-s-project-in-the-storage-module.md`.
 */
public fun interface ProjectDetector {
    /**
     * @param rootPath the folder the user picked.
     * @return the project, or unavailable with a reason the user can act on, such as the folder not
     *   being a Gradle project at all.
     */
    public fun detect(rootPath: String): Availability<AndroidProject>
}
