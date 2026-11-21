package com.replay.core.metrics;

import com.replay.core.operations.EventSimulation;
import com.replay.core.operations.SimulationLogEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computes {@link SimulationMetrics} from the event log of a completed simulation.
 *
 * <p>Design decisions:
 * <ul>
 *   <li><b>Analytics are separate from operations.</b> This class only reads the log; the
 *       engine and EventSimulation know nothing about metrics.</li>
 *   <li><b>The core method is a pure function</b> of (log, desk IDs), so it can be tested
 *       with hand-written logs, including edge cases the real simulation never produces.</li>
 *   <li><b>The log is validated strictly.</b> An inconsistent log (service ended that never
 *       started, a desk serving two people, time going backwards, ...) throws
 *       IllegalArgumentException rather than producing plausible-looking wrong numbers.</li>
 *   <li><b>Observation window</b> = [time of first ARRIVAL, time of last SERVICE_END]. If nobody
 *       arrived, or nothing completed, the window has length 0 and every window-based rate
 *       (throughput, utilisation) is reported as 0.0 instead of dividing by zero.</li>
 *   <li><b>Only completed attendees</b> enter waiting-time and time-in-system statistics, and
 *       only completed services count towards desk busy time. For a finished EventSimulation
 *       this is everyone; it matters only for partial hand-built logs.</li>
 *   <li><b>Queue length</b> is measured after all events at the same timestamp have been
 *       applied, so zero-duration visits to the queue are not counted (see docs/metrics.md).</li>
 * </ul>
 */
public final class MetricsCalculator {

    private MetricsCalculator() {
    }

    /**
     * Calculates metrics for a simulation that has been run.
     *
     * @throws IllegalStateException if {@code simulation.run()} has not been called
     */
    public static SimulationMetrics calculate(EventSimulation simulation) {
        Objects.requireNonNull(simulation, "simulation must not be null");
        if (!simulation.hasRun()) {
            throw new IllegalStateException("The simulation has not been run yet; call run() first");
        }
        return calculate(simulation.log(), simulation.deskIds());
    }

    /**
     * Calculates metrics from a chronological event log.
     *
     * @param log     entries in non-decreasing time order
     * @param deskIds every desk of the run, including desks that served nobody
     * @throws IllegalArgumentException if the log is inconsistent or deskIds contains duplicates
     */
    public static SimulationMetrics calculate(List<SimulationLogEntry> log, List<String> deskIds) {
        Objects.requireNonNull(log, "log must not be null");
        Objects.requireNonNull(deskIds, "deskIds must not be null");

        Map<String, DeskTally> desks = new LinkedHashMap<>();
        for (String deskId : deskIds) {
            Objects.requireNonNull(deskId, "deskIds must not contain null");
            if (desks.put(deskId, new DeskTally()) != null) {
                throw new IllegalArgumentException("Duplicate desk id: " + deskId);
            }
        }

        Map<String, AttendeeTrace> attendees = new LinkedHashMap<>();
        Set<String> queued = new HashSet<>();
        int maximumQueueLength = 0;
        boolean anyArrival = false;
        boolean anyCompletion = false;
        double observationStart = 0.0;
        double observationEnd = 0.0;
        double previousTime = 0.0;

        for (int i = 0; i < log.size(); i++) {
            SimulationLogEntry entry = Objects.requireNonNull(log.get(i), "log must not contain null entries");
            double time = entry.time();
            if (time < previousTime) {
                throw new IllegalArgumentException("Log is not chronological at t=" + time + " (after t=" + previousTime + ")");
            }
            previousTime = time;
            String id = entry.attendeeId();

            switch (entry.type()) {
                case ARRIVAL -> {
                    if (attendees.containsKey(id)) {
                        throw new IllegalArgumentException("Attendee " + id + " arrives more than once");
                    }
                    attendees.put(id, new AttendeeTrace(time));
                    if (!anyArrival) {
                        anyArrival = true;
                        observationStart = time; // log is chronological, so this is the first arrival
                    }
                }
                case QUEUED -> {
                    requireTrace(attendees, entry);
                    if (!queued.add(id)) {
                        throw new IllegalArgumentException("Attendee " + id + " joins the queue twice");
                    }
                }
                case SERVICE_START -> {
                    AttendeeTrace trace = requireTrace(attendees, entry);
                    DeskTally desk = requireDesk(desks, entry);
                    if (trace.serviceStart != null) {
                        throw new IllegalArgumentException("Attendee " + id + " starts service twice");
                    }
                    if (desk.openStart != null) {
                        throw new IllegalArgumentException("Desk " + entry.deskId() + " starts a service while busy (t=" + time + ")");
                    }
                    trace.serviceStart = time;
                    trace.deskId = entry.deskId();
                    desk.openStart = time;
                    queued.remove(id); // leaves the queue if they were in it
                }
                case SERVICE_END -> {
                    AttendeeTrace trace = requireTrace(attendees, entry);
                    DeskTally desk = requireDesk(desks, entry);
                    if (trace.serviceStart == null || trace.serviceEnd != null) {
                        throw new IllegalArgumentException("Attendee " + id + " ends a service that is not in progress");
                    }
                    if (!entry.deskId().equals(trace.deskId) || desk.openStart == null) {
                        throw new IllegalArgumentException("Service of " + id + " ends at a desk that was not serving them");
                    }
                    trace.serviceEnd = time;
                    desk.busyTime += time - desk.openStart;
                    desk.openStart = null;
                    desk.served++;
                    anyCompletion = true;
                    observationEnd = time; // chronological, so the last one wins
                }
            }

            // Measure the queue only once every event at this timestamp has been applied.
            boolean endOfInstant = i == log.size() - 1 || log.get(i + 1).time() != time;
            if (endOfInstant) {
                maximumQueueLength = Math.max(maximumQueueLength, queued.size());
            }
        }

        // Window edge cases: no arrivals -> [0,0]; arrivals but no completion -> [start,start].
        if (!anyArrival) {
            observationStart = 0.0;
            observationEnd = 0.0;
        } else if (!anyCompletion) {
            observationEnd = observationStart;
        }
        double window = observationEnd - observationStart;

        int completed = 0;
        double sumWaiting = 0.0;
        double maxWaiting = 0.0;
        double sumInSystem = 0.0;
        for (AttendeeTrace trace : attendees.values()) {
            if (trace.serviceEnd != null) {
                completed++;
                double waiting = trace.serviceStart - trace.arrival;
                sumWaiting += waiting;
                maxWaiting = Math.max(maxWaiting, waiting);
                sumInSystem += trace.serviceEnd - trace.arrival;
            }
        }

        List<DeskMetrics> deskMetrics = new ArrayList<>();
        double totalBusy = 0.0;
        for (Map.Entry<String, DeskTally> e : desks.entrySet()) {
            DeskTally tally = e.getValue();
            totalBusy += tally.busyTime;
            deskMetrics.add(new DeskMetrics(e.getKey(), tally.served, tally.busyTime, percent(tally.busyTime, window)));
        }

        return new SimulationMetrics(
                attendees.size(),
                completed,
                completed == 0 ? 0.0 : sumWaiting / completed,
                maxWaiting,
                completed == 0 ? 0.0 : sumInSystem / completed,
                maximumQueueLength,
                observationStart,
                observationEnd,
                window > 0 ? completed / window : 0.0,
                percent(totalBusy, desks.size() * window),
                deskMetrics);
    }

    /** part / whole * 100, or 0.0 when whole is not positive (avoids division by zero). */
    private static double percent(double part, double whole) {
        return whole > 0 ? part / whole * 100.0 : 0.0;
    }

    private static AttendeeTrace requireTrace(Map<String, AttendeeTrace> attendees, SimulationLogEntry entry) {
        AttendeeTrace trace = attendees.get(entry.attendeeId());
        if (trace == null) {
            throw new IllegalArgumentException("Attendee " + entry.attendeeId() + " has an entry at t="
                    + entry.time() + " but no earlier ARRIVAL");
        }
        return trace;
    }

    private static DeskTally requireDesk(Map<String, DeskTally> desks, SimulationLogEntry entry) {
        DeskTally desk = entry.deskId() == null ? null : desks.get(entry.deskId());
        if (desk == null) {
            throw new IllegalArgumentException("Unknown or missing desk id in entry for " + entry.attendeeId()
                    + " at t=" + entry.time() + ": " + entry.deskId());
        }
        return desk;
    }

    /** Times recorded for one attendee while scanning the log. */
    private static final class AttendeeTrace {
        final double arrival;
        Double serviceStart;
        Double serviceEnd;
        String deskId;

        AttendeeTrace(double arrival) {
            this.arrival = arrival;
        }
    }

    /** Running totals for one desk while scanning the log. */
    private static final class DeskTally {
        Double openStart; // start time of the service in progress, or null if idle
        double busyTime;
        int served;
    }
}