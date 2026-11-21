package com.replay.core.engine;

import java.util.Objects;
import java.util.PriorityQueue;

/**
 * A discrete-event simulation engine.
 *
 * <p>The engine keeps a clock and a priority queue of pending events. Its loop
 * is: take the earliest event, jump the clock to that event's time, run its
 * action (which may schedule more events), and repeat. Time never advances
 * between events; it jumps from one event to the next.
 *
 * <p>Design decisions:
 * <ul>
 *   <li><b>Time unit:</b> minutes, stored as a {@code double}.</li>
 *   <li><b>Determinism:</b> ties in time are broken by a sequence number (FIFO).</li>
 *   <li><b>No time travel:</b> an event cannot be scheduled before the current
 *       time, so the clock never goes backwards.</li>
 *   <li><b>{@code runUntil(t)} is inclusive:</b> events at exactly {@code t}
 *       run. Afterwards the clock is set to {@code t}, even if no event happened
 *       exactly then, and later events stay pending.</li>
 *   <li><b>{@code run()}</b> leaves the clock at the time of the last event it processed.</li>
 *   <li><b>Failing events:</b> if an action throws, the exception propagates,
 *       that event is removed but not counted as processed, the clock stays at
 *       its time, and all other events remain pending. The engine stays usable.</li>
 *   <li><b>No re-entrancy:</b> an event may not call {@code run()} or
 *       {@code runUntil()}.</li>
 * </ul>
 *
 * <p>Not thread-safe. A simulation is single-threaded by design, which is what
 * makes it deterministic.
 */
public final class SimulationEngine {

    private final PriorityQueue<ScheduledEvent> pendingEvents = new PriorityQueue<>();
    private final SimulationContext context = new EngineContext();

    private double currentTime = 0.0;
    private long nextSequence = 0;
    private long processedEventCount = 0;
    private boolean running = false;

    /** The current simulation time, in minutes. Starts at 0. */
    public double now() {
        return currentTime;
    }

    /** How many events have been fully executed so far. */
    public long processedEventCount() {
        return processedEventCount;
    }

    /** How many events are still waiting to run. */
    public int pendingEventCount() {
        return pendingEvents.size();
    }

    /**
     * Schedules an event at an absolute simulation time.
     *
     * @throws IllegalArgumentException if the time is negative, NaN, infinite
     *                                  or earlier than {@link #now()}
     * @throws NullPointerException     if description or action is null
     */
    public void scheduleAt(double time, String description, EventAction action) {
        // Null checks come first so a bad call does not consume a sequence number
        // and so the error messages below can safely mention the description.
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(action, "action must not be null");

        double validTime = ScheduledEvent.requireValidTime(time, "time");
        if (validTime < currentTime) {
            throw new IllegalArgumentException("Cannot schedule \"" + description + "\" at t="
                    + validTime + " because the current time is already t=" + currentTime);
        }
        pendingEvents.add(new ScheduledEvent(validTime, nextSequence++, description, action));
    }

    /**
     * Schedules an event {@code delay} minutes after the current time.
     *
     * @throws IllegalArgumentException if the delay is negative, NaN or infinite
     * @throws NullPointerException     if description or action is null
     */
    public void scheduleAfter(double delay, String description, EventAction action) {
        double validDelay = ScheduledEvent.requireValidTime(delay, "delay");
        scheduleAt(currentTime + validDelay, description, action);
    }

    /**
     * Processes every pending event, including events scheduled while running,
     * until none are left. The clock ends at the time of the last event.
     *
     * @throws IllegalStateException if called while the engine is already running
     */
    public void run() {
        runLoop(Double.POSITIVE_INFINITY, false);
    }

    /**
     * Processes events whose time is less than or equal to {@code endTime}, then
     * sets the clock to {@code endTime}. Later events remain pending and can be
     * processed by a later {@code run()} or {@code runUntil()}.
     *
     * @throws IllegalArgumentException if endTime is negative, NaN, infinite or
     *                                  earlier than the current time
     * @throws IllegalStateException    if called while the engine is already running
     */
    public void runUntil(double endTime) {
        double limit = ScheduledEvent.requireValidTime(endTime, "endTime");
        if (limit < currentTime) {
            throw new IllegalArgumentException("endTime t=" + limit
                    + " is earlier than the current time t=" + currentTime);
        }
        runLoop(limit, true);
    }

    /** The single event loop shared by run() and runUntil(). */
    private void runLoop(double limit, boolean advanceClockToLimit) {
        if (running) {
            throw new IllegalStateException(
                    "The engine is already running; run()/runUntil() cannot be called from an event");
        }
        running = true;
        try {
            while (true) {
                ScheduledEvent next = pendingEvents.peek();
                if (next == null || next.time() > limit) {
                    break; // nothing left, or the next event is beyond the limit (left pending)
                }
                pendingEvents.poll();
                currentTime = next.time();
                next.action().execute(context);
                processedEventCount++;
            }
            if (advanceClockToLimit) {
                currentTime = limit;
            }
        } finally {
            running = false; // always reset, even if an action threw
        }
    }

    /** The context handed to events: forwards to the engine but hides run controls. */
    private final class EngineContext implements SimulationContext {

        @Override
        public double now() {
            return currentTime;
        }

        @Override
        public void scheduleAt(double time, String description, EventAction action) {
            SimulationEngine.this.scheduleAt(time, description, action);
        }

        @Override
        public void scheduleAfter(double delay, String description, EventAction action) {
            SimulationEngine.this.scheduleAfter(delay, description, action);
        }
    }
}