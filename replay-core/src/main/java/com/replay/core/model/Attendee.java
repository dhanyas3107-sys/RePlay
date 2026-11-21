
package com.replay.core.model;

import java.util.Objects;

/**
 * A person who arrives at the registration area.
 *
 * <p>Immutable: an attendee is just an identity and an arrival time. What
 * happens to them (waiting, service) is recorded by the simulation, not stored
 * here. Uniqueness of IDs is enforced by {@code EventSimulation}, which sees the
 * whole collection.
 *
 * @param id          non-blank identifier, e.g. "A1"
 * @param arrivalTime arrival time in simulation minutes (finite, >= 0)
 */
public record Attendee(String id, double arrivalTime) {

    public Attendee {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (Double.isNaN(arrivalTime) || Double.isInfinite(arrivalTime) || arrivalTime < 0) {
            throw new IllegalArgumentException(
                    "arrivalTime must be a finite number >= 0, but was " + arrivalTime);
        }
        arrivalTime = arrivalTime + 0.0; // normalises -0.0 to 0.0
    }
}