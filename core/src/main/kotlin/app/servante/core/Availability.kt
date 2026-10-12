package app.servante.core

/**
 * Either a value Servante could read, or the reason it could not.
 *
 * The product rule this type exists for: anything that cannot be read is surfaced as unavailable with a
 * reason the user can act on. There is no third case, no placeholder and no guessed number, so a screen
 * cannot accidentally show a zero that means "unknown".
 */
public sealed interface Availability<out T> {
    public data class Available<out T>(public val value: T) : Availability<T>

    /**
     * @param reason what stopped the read, phrased for the person reading the screen. Never blank: an
     *   unavailable value with no reason is the placeholder this type exists to prevent.
     */
    public data class Unavailable<out T>(public val reason: String) : Availability<T> {
        init {
            require(reason.isNotBlank()) { "An unavailable value needs a reason the user can read." }
        }
    }

    public fun valueOrNull(): T? = when (this) {
        is Available -> value
        is Unavailable -> null
    }

    public fun reasonOrNull(): String? = when (this) {
        is Available -> null
        is Unavailable -> reason
    }
}

/** Transforms an available value, carrying an unavailable one through untouched. */
public inline fun <T, R> Availability<T>.map(transform: (T) -> R): Availability<R> = when (this) {
    is Availability.Available -> Availability.Available(transform(value))
    is Availability.Unavailable -> Availability.Unavailable(reason)
}

/**
 * Chains a read that can itself be unavailable, carrying the first reason through.
 *
 * What it is for: a sequence of reads where each one needs the previous one's value, as in "find the
 * folder, then find the settings file in it, then read it". Written as nested `when` blocks, that becomes
 * a function with a return statement per failure; written with this, each step says what it needs and the
 * reason the user sees is the one from the step that actually failed.
 */
public inline fun <T, R> Availability<T>.flatMap(transform: (T) -> Availability<R>): Availability<R> = when (this) {
    is Availability.Available -> transform(value)
    is Availability.Unavailable -> Availability.Unavailable(reason)
}

/** Wraps a value that was read successfully. */
public fun <T> T.available(): Availability<T> = Availability.Available(this)

/** States that a value could not be read, and why. */
public fun <T> unavailable(reason: String): Availability<T> = Availability.Unavailable(reason)
