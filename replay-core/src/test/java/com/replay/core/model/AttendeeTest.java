package com.replay.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttendeeTest {

    @Test
    void storesIdAndArrivalTime() {
        Attendee attendee = new Attendee("A1", 2.5);
        assertEquals("A1", attendee.id());
        assertEquals(2.5, attendee.arrivalTime());
    }

    @Test
    void rejectsNullId() {
        assertThrows(NullPointerException.class, () -> new Attendee(null, 1.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void rejectsBlankId(String id) {
        assertThrows(IllegalArgumentException.class, () -> new Attendee(id, 1.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.5, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidArrivalTime(double time) {
        assertThrows(IllegalArgumentException.class, () -> new Attendee("A1", time));
    }

    @Test
    void allowsArrivalAtTimeZero() {
        assertEquals(0.0, new Attendee("A1", 0.0).arrivalTime());
    }
}