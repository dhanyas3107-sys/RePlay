package com.replay.core.engine;

/**
 * The limited view of the engine that is handed to a running event.
 *
 * <p>Design decision: events receive this small interface instead of the
 * {@link SimulationEngine} itself. An event can therefore do only what an event
 * should do (read the time, schedule more events) and cannot call
 * {@code run()}. It also keeps event code decoupled from the engine class.
 */
public interface SimulationContext {

    /** The current simulation time, in minutes. */
    double now();

    /**
     * Schedules an event at an absolute simulation time.
     *
     * @throws IllegalArgumentException if the time is negative, NaN, infinite
     *                                  or earlier than {@link #now()}
     * @throws NullPointerException     if description or action is null
     */
    void scheduleAt(double time, String description, EventAction action);

    /**
     * Schedules an event {@code delay} minutes after the current time. A delay
     * of 0 is allowed and runs after every event already scheduled for this
     * same instant.
     *
     * @throws IllegalArgumentException if the delay is negative, NaN or infinite
     * @throws NullPointerException     if description or action is null
     */
    void scheduleAfter(double delay, String description, EventAction action);
}
