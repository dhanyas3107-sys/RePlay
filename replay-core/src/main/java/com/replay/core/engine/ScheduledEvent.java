package com.replay.core.engine;

import java.util.Objects;

/**
 * An event waiting in the engine's queue.
 *
 * <p>Events are ordered by {@code time} first and {@code sequence} second. The
 * sequence number is assigned by the engine and increases with every event
 * scheduled, so events with the same time run in the order they were scheduled
 * (FIFO). Without this tie-breaker, {@link java.util.PriorityQueue} gives no
 * guarantee about the order of equal elements, and the same input could
 * produce different results.
 *
 * <p>This type is package-private on purpose: it is an internal detail of the
 * engine, and callers never need to construct one or hand-pick a sequence
 * number.
 *
 * @param time        when the event runs, in simulation minutes (finite, >= 0)
 * @param sequence    tie-breaker, unique per engine
 * @param description human-readable label, useful for debugging and traces
 * @param action      what to do when the event runs
 */
record ScheduledEvent(double time, long sequence, String description, EventAction action)
        implements Comparable<ScheduledEvent> {

    ScheduledEvent {
        time = requireValidTime(time, "time");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }

    /**
     * Shared rule for every time or delay in the engine: finite and not negative.
     * Returns the value with -0.0 normalised to 0.0, because
     * {@code Double.compare} treats -0.0 as smaller than 0.0, which would
     * wrongly break the time ordering.
     */
    static double requireValidTime(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            throw new IllegalArgumentException(
                    name + " must be a finite number >= 0, but was " + value);
        }
        return value + 0.0;
    }

    @Override
    public int compareTo(ScheduledEvent other) {
        int byTime = Double.compare(time, other.time);
        return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
    }

    @Override
    public String toString() {
        return "ScheduledEvent[t=" + time + ", #" + sequence + ", " + description + "]";
    }
}