package org.graphiks.kadre.internal.runtime

/**
 * Adds [left] and [right], failing instead of silently wrapping on overflow.
 *
 * Invariant: the result is the exact mathematical sum, and a sum that does not fit in a `Long`
 * fails the `check` below with an explicit message rather than wrapping. Callers count events,
 * bytes and identities with this helper, and a wrapped counter would move a revision backwards
 * (or an identity to a huge value) with no diagnostics at all — precisely the kind of silent
 * corruption the ordinary-input pipeline must not have. This is the common replacement for
 * `Math.addExact`, which is JVM-only, and it keeps the `check`-with-an-explicit-message idiom the
 * runtime already uses for its exhausted identity spaces.
 */
internal fun checkedAdd(left: Long, right: Long): Long {
    val sum = left + right
    // Adding two values of the same sign can only leave the range by flipping that sign.
    val overflowed = (left < 0L) == (right < 0L) && (left < 0L) != (sum < 0L)
    check(!overflowed) { "checked add space exhausted: $left + $right" }
    return sum
}
