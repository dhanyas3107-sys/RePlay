package com.replay.core.metrics;

import java.util.List;

/**
 * Immutable summary of one completed simulation run. Full definitions are in docs/metrics.md.
 *
 * <p>All times are in simulation minutes and are never rounded here (rounding is a display concern).
 * Averages and rates are 0.0 when there is no data to compute them from, so read them together
 * with {@code completedAttendees}.
 *
 * @param totalAttendees            attendees who arrived
 * @param completedAttendees        attendees whose service finished
 * @param averageWaitingTime        mean of (service start - arrival) over completed attendees
 * @param maximumWaitingTime        largest such waiting time
 * @param averageTimeInSystem       mean of (service end - arrival) over completed attendees
 * @param maximumQueueLength        largest queue length that persisted for positive time
 * @param observationStart          time of the first arrival (0.0 if nobody arrived)
 * @param observationEnd            time of the final service completion (equals start if nothing completed)
 * @param throughputPerMinute       completedAttendees / total simulation duration
 * @param overallUtilisationPercent total busy time of all desks / (desk count * duration) * 100
 * @param deskMetrics               one entry per desk, in the order the desks were supplied
 */
public record SimulationMetrics(
        int totalAttendees,
        int completedAttendees,
        double averageWaitingTime,
        double maximumWaitingTime,
        double averageTimeInSystem,
        int maximumQueueLength,
        double observationStart,
        double observationEnd,
        double throughputPerMinute,
        double overallUtilisationPercent,
        List<DeskMetrics> deskMetrics) {

    public SimulationMetrics {
        if (totalAttendees < 0 || completedAttendees < 0 || completedAttendees > totalAttendees) {
            throw new IllegalArgumentException("Inconsistent attendee counts: total=" + totalAttendees
                    + ", completed=" + completedAttendees);
        }
        if (maximumQueueLength < 0) {
            throw new IllegalArgumentException("maximumQueueLength must not be negative");
        }
        if (observationEnd < observationStart) {
            throw new IllegalArgumentException("observationEnd must not be before observationStart");
        }
        deskMetrics = List.copyOf(deskMetrics); // immutable defensive copy
    }

    /** Length of the observation window: observationEnd - observationStart. */
    public double totalSimulationDuration() {
        return observationEnd - observationStart;
    }
}