package com.replay.core.operations;

import java.util.Locale;
import java.util.Objects;

/**
 * One line of the chronological event log produced by {@code EventSimulation}.
 *
 * <p>This is a raw record of what happened. Interpretation (average waits,
 * utilisation and so on) belongs to the later metrics/analysis phase.
 *
 * <ul>
 *   <li>{@code ARRIVAL}: attendee arrived.</li>
 *   <li>{@code QUEUED}: attendee arrived and no desk was free at that moment.</li>
 *   <li>{@code SERVICE_START}: a desk started serving the attendee.</li>
 *   <li>{@code SERVICE_END}: a desk finished serving the attendee.</li>
 * </ul>
 *
 * @param deskId the desk involved, or {@code null} for ARRIVAL and QUEUED
 */
public record SimulationLogEntry(double time, Type type, String attendeeId, String deskId) {

    public enum Type { ARRIVAL, QUEUED, SERVICE_START, SERVICE_END }

    public SimulationLogEntry {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(attendeeId, "attendeeId must not be null");
    }

    @Override
    public String toString() {
        String message = switch (type) {
            case ARRIVAL -> attendeeId + " arrives";
            case QUEUED -> attendeeId + " waits in the queue";
            case SERVICE_START -> attendeeId + " starts service at " + deskId;
            case SERVICE_END -> attendeeId + " finishes service at " + deskId;
        };
        return String.format(Locale.ROOT, "[t=%5.1f] %s", time, message);
    }
}