package com.replay.core.operations;

import com.replay.core.engine.SimulationContext;
import com.replay.core.engine.SimulationEngine;
import com.replay.core.model.Attendee;
import com.replay.core.model.ServiceDesk;
import com.replay.core.operations.SimulationLogEntry.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A deterministic event-registration simulation: attendees arrive at known
 * times, queue in one shared FIFO line, and are served by one of several desks
 * that each take a fixed time.
 *
 * <p>This class only coordinates. All time-ordering and event-processing is done
 * by the existing {@link SimulationEngine}; the attendee/desk/queue rules live here.
 *
 * <h2>Rules and assumptions</h2>
 * <ul>
 *   <li><b>One shared FIFO queue.</b> An attendee who finds no free desk joins
 *       the back of the queue. When a desk frees up, the front of the queue is served.</li>
 *   <li><b>Free desk choice.</b> If several desks are free, the one that appears
 *       first in the desk list is used.</li>
 *   <li><b>Simultaneous arrivals</b> are processed in the order of the attendee
 *       list. This is why the input is a {@code List}: a Set has no defined order.
 *       (The engine's FIFO tie-break does the rest.)</li>
 *   <li><b>Arrival vs completion at the same instant.</b> All arrivals are
 *       scheduled before the run starts, whereas completions are scheduled while
 *       it runs, so they have later sequence numbers. At the same timestamp,
 *       arrivals are therefore processed <em>before</em> completions. The newcomer
 *       sees the desk as still busy and joins the back of the queue, so it never
 *       jumps ahead of attendees already waiting. If the queue was empty it is
 *       served in that same instant, with 0 minutes waited. The log shows QUEUED
 *       followed by SERVICE_START at the same time.</li>
 *   <li><b>Simultaneous completions</b> are processed in the order the services
 *       started (earlier start first), and each freed desk takes the next person
 *       in the queue immediately.</li>
 *   <li>A desk is never left idle while someone waits: the queue is non-empty
 *       only while every desk is busy.</li>
 * </ul>
 *
 * <p>A simulation can be run only once, because the desks are mutated during
 * the run. Not thread-safe.
 */
public final class EventSimulation {

    private final SimulationEngine engine = new SimulationEngine();
    private final List<Attendee> attendees;
    private final List<ServiceDesk> desks;
    private final Deque<Attendee> waitingQueue = new ArrayDeque<>();
    private final List<SimulationLogEntry> log = new ArrayList<>();
    private boolean hasRun = false;

    /**
     * @param attendees the arrivals (may be empty); order decides ties at equal arrival times
     * @param desks     the registration desks (at least one); order decides which free desk is used first
     * @throws NullPointerException     if a list or any element is null
     * @throws IllegalArgumentException if there are no desks, an attendee ID or
     *                                  desk ID is duplicated, or a desk is already occupied
     */
    public EventSimulation(List<Attendee> attendees, List<ServiceDesk> desks) {
        Objects.requireNonNull(attendees, "attendees must not be null");
        Objects.requireNonNull(desks, "desks must not be null");
        // List.copyOf makes a defensive copy and rejects null elements.
        this.attendees = List.copyOf(attendees);
        this.desks = List.copyOf(desks);

        if (this.desks.isEmpty()) {
            throw new IllegalArgumentException("At least one service desk is required");
        }
        Set<String> attendeeIds = new HashSet<>();
        for (Attendee attendee : this.attendees) {
            if (!attendeeIds.add(attendee.id())) {
                throw new IllegalArgumentException("Duplicate attendee id: " + attendee.id());
            }
        }
        Set<String> deskIds = new HashSet<>();
        for (ServiceDesk desk : this.desks) {
            if (!deskIds.add(desk.id())) {
                throw new IllegalArgumentException("Duplicate desk id: " + desk.id());
            }
            if (!desk.isAvailable()) {
                throw new IllegalArgumentException("Desk " + desk.id() + " must be available at the start");
            }
        }
    }

    /**
     * Schedules every arrival and runs the simulation until nothing is left to do.
     *
     * @throws IllegalStateException if the simulation has already been run
     */
    public void run() {
        if (hasRun) {
            throw new IllegalStateException("This simulation has already been run");
        }
        hasRun = true;
        for (Attendee attendee : attendees) {
            engine.scheduleAt(attendee.arrivalTime(), "Arrival of " + attendee.id(),
                    ctx -> handleArrival(attendee, ctx));
        }
        engine.run();
    }

    /** The chronological log of what happened (an immutable snapshot). */
    public List<SimulationLogEntry> log() {
        return List.copyOf(log);
    }

    /** Current simulation time; after {@link #run()} this is the time of the last event. */
    public double now() {
        return engine.now();
    }

    public long processedEventCount() {
        return engine.processedEventCount();
    }

    /** Number of attendees currently waiting (0 after a completed run). */
    public int waitingCount() {
        return waitingQueue.size();
    }

    // ---- event handlers ----

    private void handleArrival(Attendee attendee, SimulationContext ctx) {
        logEvent(ctx, Type.ARRIVAL, attendee, null);
        ServiceDesk desk = firstAvailableDesk();
        if (desk != null) {
            startService(desk, attendee, ctx);
        } else {
            waitingQueue.addLast(attendee);
            logEvent(ctx, Type.QUEUED, attendee, null);
        }
    }

    private void handleServiceEnd(ServiceDesk desk, SimulationContext ctx) {
        Attendee finished = desk.finishService();
        logEvent(ctx, Type.SERVICE_END, finished, desk);

        Attendee next = waitingQueue.pollFirst(); // front of the FIFO queue, or null
        if (next != null) {
            startService(desk, next, ctx);
        }
    }

    private void startService(ServiceDesk desk, Attendee attendee, SimulationContext ctx) {
        desk.startService(attendee);
        logEvent(ctx, Type.SERVICE_START, attendee, desk);
        ctx.scheduleAfter(desk.serviceDuration(),
                "Service end of " + attendee.id() + " at " + desk.id(),
                c -> handleServiceEnd(desk, c));
    }

    // ---- helpers ----

    private ServiceDesk firstAvailableDesk() {
        for (ServiceDesk desk : desks) {
            if (desk.isAvailable()) {
                return desk;
            }
        }
        return null;
    }

    private void logEvent(SimulationContext ctx, Type type, Attendee attendee, ServiceDesk desk) {
        log.add(new SimulationLogEntry(ctx.now(), type, attendee.id(), desk == null ? null : desk.id()));
    }

    /** IDs of the desks, in the order they were supplied (read-only; used by the metrics layer). */
    public List<String> deskIds() {
        return desks.stream().map(ServiceDesk::id).toList();
    }

    /** True once {@link #run()} has been called. */
    public boolean hasRun() {
        return hasRun;
    }
}
