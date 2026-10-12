package app.servante.core.fakes

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A clock that does not move unless a test moves it.
 *
 * Time is injected everywhere in Servante for this reason: the audit log, the "checked 2 minutes ago"
 * label and the certificate validity warnings are all time-dependent, and a test that depends on the
 * real clock is a test that fails on a slow machine.
 */
public class FixedClock(private var now: Instant = Instant.parse("2026-10-12T20:30:00Z")) : Clock {
    override fun now(): Instant = now

    public fun advanceTo(instant: Instant) {
        now = instant
    }
}
