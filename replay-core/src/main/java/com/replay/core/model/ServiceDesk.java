package com.replay.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * A registration counter that serves one attendee at a time for a fixed duration.
 *
 * <p>A desk is either <em>available</em> or <em>occupied</em>. The only ways to
 * change that are {@link #startService} and {@link #finishService}, and both
 * refuse invalid transitions, so the desk can never hold two attendees or
 * finish a service that never started.
 *
 * <p>The desk is mutable and is changed while a simulation runs. It does not
 * schedule anything itself; {@code EventSimulation} does that.
 */
public final class ServiceDesk {

    private final String id;
    private final double serviceDuration;
    private Attendee currentAttendee; // null means available

    /**
     * @param id              non-blank identifier, e.g. "D1"
     * @param serviceDuration minutes needed to serve one attendee (finite, > 0)
     */
    public ServiceDesk(String id, double serviceDuration) {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (Double.isNaN(serviceDuration) || Double.isInfinite(serviceDuration) || serviceDuration <= 0) {
            throw new IllegalArgumentException(
                    "serviceDuration must be a finite number > 0, but was " + serviceDuration);
        }
        this.id = id;
        this.serviceDuration = serviceDuration;
    }

    public String id() {
        return id;
    }

    public double serviceDuration() {
        return serviceDuration;
    }

    public boolean isAvailable() {
        return currentAttendee == null;
    }

    /** The attendee currently being served, or empty if the desk is available. */
    public Optional<Attendee> currentAttendee() {
        return Optional.ofNullable(currentAttendee);
    }

    /**
     * Begins serving an attendee.
     *
     * @throws IllegalStateException if the desk is already occupied
     */
    public void startService(Attendee attendee) {
        Objects.requireNonNull(attendee, "attendee must not be null");
        if (currentAttendee != null) {
            throw new IllegalStateException("Desk " + id + " is already serving " + currentAttendee.id());
        }
        currentAttendee = attendee;
    }

    /**
     * Ends the current service and makes the desk available.
     *
     * @return the attendee who was just served
     * @throws IllegalStateException if the desk is not occupied
     */
    public Attendee finishService() {
        if (currentAttendee == null) {
            throw new IllegalStateException("Desk " + id + " is not serving anyone");
        }
        Attendee finished = currentAttendee;
        currentAttendee = null;
        return finished;
    }

    @Override
    public String toString() {
        return "ServiceDesk[" + id + ", duration=" + serviceDuration
                + (currentAttendee == null ? ", available" : ", serving " + currentAttendee.id()) + "]";
    }
}