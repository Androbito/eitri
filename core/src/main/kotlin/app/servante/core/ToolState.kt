package app.servante.core

/**
 * The six states every tool screen implements, drawn as patterns in `design/screens/Components.dc.html`.
 *
 * A screen renders one of these and nothing else, so "no data yet" can never be mistaken for "nothing
 * wrong": [FirstRun] and [Healthy] are different states with different words.
 */
public sealed interface ToolState<out T> {
    /** Nothing has been checked yet for this project. */
    public data object FirstRun : ToolState<Nothing>

    public data class Loading(public val progress: Progress) : ToolState<Nothing>

    /** Checked, nothing wrong. [result] carries how many checks passed. */
    public data class Healthy<out T>(public val result: T) : ToolState<T>

    public data class Findings<out T>(public val result: T) : ToolState<T>

    /** The tool could not run at all, and why. Rendered as muted text in a dashed outline. */
    public data class Unavailable(public val reason: String, public val action: SuggestedAction? = null) :
        ToolState<Nothing> {
        init {
            require(reason.isNotBlank()) { "An unavailable tool needs a reason the user can read." }
        }
    }

    /**
     * The check started and stopped. [nothingChanged] is stated in the UI on purpose: after a failed run
     * the first question a user has is whether Servante touched their project.
     */
    public data class Error(
        public val message: String,
        public val detail: String? = null,
        public val nothingChanged: Boolean = true,
    ) : ToolState<Nothing>
}

/** A way out of an unavailable state, such as the "Connect a service account" link in the designs. */
public data class SuggestedAction(public val label: String, public val target: String)

/**
 * Progress of a running check, as the loading pattern shows it: "Reading 3 keystores, step 2 of 4."
 *
 * @param step 1-based, so the first step reads as "step 1 of 4".
 */
public data class Progress(public val message: String, public val step: Int, public val totalSteps: Int) {
    init {
        require(totalSteps > 0) { "A progress needs at least one step." }
        require(step in 1..totalSteps) { "Step $step is outside 1..$totalSteps." }
    }

    public val fraction: Float get() = step.toFloat() / totalSteps.toFloat()

    public companion object {
        public fun indeterminate(message: String): Progress = Progress(message, step = 1, totalSteps = 1)
    }
}
