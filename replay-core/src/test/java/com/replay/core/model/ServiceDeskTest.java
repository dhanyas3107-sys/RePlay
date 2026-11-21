package com.replay.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceDeskTest {

    private static final Attendee A1 = new Attendee("A1", 0.0);
    private static final Attendee A2 = new Attendee("A2", 1.0);

    @Test
    void newDeskIsAvailable() {
        ServiceDesk desk = new ServiceDesk("D1", 4.0);
        assertTrue(desk.isAvailable());
        assertTrue(desk.currentAttendee().isEmpty());
        assertEquals("D1", desk.id());
        assertEquals(4.0, desk.serviceDuration());
    }

    @Test
    void startingServiceOccupiesTheDesk() {
        ServiceDesk desk = new ServiceDesk("D1", 4.0);
        desk.startService(A1);
        assertFalse(desk.isAvailable());
        assertEquals(A1, desk.currentAttendee().orElseThrow());
    }

    @Test
    void finishingServiceFreesTheDeskAndReturnsTheAttendee() {
        ServiceDesk desk = new ServiceDesk("D1", 4.0);
        desk.startService(A1);
        assertEquals(A1, desk.finishService());
        assertTrue(desk.isAvailable());
    }

    @Test
    void cannotStartServiceOnOccupiedDesk() {
        ServiceDesk desk = new ServiceDesk("D1", 4.0);
        desk.startService(A1);
        assertThrows(IllegalStateException.class, () -> desk.startService(A2));
        assertEquals(A1, desk.currentAttendee().orElseThrow()); // state unchanged
    }

    @Test
    void cannotFinishServiceOnIdleDesk() {
        ServiceDesk desk = new ServiceDesk("D1", 4.0);
        assertThrows(IllegalStateException.class, desk::finishService);
    }

    @Test
    void deskCanBeReusedAfterFinishing() {
        ServiceDesk desk = new ServiceDesk("D1", 4.0);
        desk.startService(A1);
        desk.finishService();
        desk.startService(A2);
        assertEquals(A2, desk.currentAttendee().orElseThrow());
    }

    @Test
    void rejectsNullAttendee() {
        assertThrows(NullPointerException.class, () -> new ServiceDesk("D1", 4.0).startService(null));
    }

    @Test
    void rejectsNullOrBlankId() {
        assertThrows(NullPointerException.class, () -> new ServiceDesk(null, 4.0));
        assertThrows(IllegalArgumentException.class, () -> new ServiceDesk(" ", 4.0));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsInvalidServiceDuration(double duration) {
        assertThrows(IllegalArgumentException.class, () -> new ServiceDesk("D1", duration));
    }
}