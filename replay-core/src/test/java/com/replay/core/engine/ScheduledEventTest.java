package com.replay.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduledEventTest {

    private static final EventAction NO_OP = ctx -> { };

    private static ScheduledEvent event(double time, long sequence, String name) {
        return new ScheduledEvent(time, sequence, name, NO_OP);
    }

    @Test
    void earlierTimeComesFirstRegardlessOfSequence() {
        ScheduledEvent early = event(1.0, 99, "early");
        ScheduledEvent late = event(2.0, 0, "late");
        assertTrue(early.compareTo(late) < 0);
        assertTrue(late.compareTo(early) > 0);
    }

    @Test
    void sameTimeIsOrderedBySequence() {
        ScheduledEvent first = event(5.0, 1, "first");
        ScheduledEvent second = event(5.0, 2, "second");
        assertTrue(first.compareTo(second) < 0);
        assertTrue(second.compareTo(first) > 0);
    }

    @Test
    void sortingUsesTimeThenSequence() {
        List<ScheduledEvent> events = new ArrayList<>(List.of(
                event(3.0, 0, "c"),
                event(1.0, 3, "b2"),
                event(1.0, 1, "b1"),
                event(2.0, 2, "d")));
        Collections.sort(events);
        assertEquals(List.of("b1", "b2", "d", "c"),
                events.stream().map(ScheduledEvent::description).toList());
    }

    @Test
    void negativeZeroIsNormalisedToZero() {
        ScheduledEvent e = event(-0.0, 0, "zero");
        assertEquals(Double.doubleToLongBits(0.0), Double.doubleToLongBits(e.time()));
    }

    @Test
    void rejectsInvalidTime() {
        assertThrows(IllegalArgumentException.class, () -> event(-1.0, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> event(Double.NaN, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> event(Double.POSITIVE_INFINITY, 0, "x"));
    }

    @Test
    void rejectsNullDescriptionAndAction() {
        assertThrows(NullPointerException.class, () -> new ScheduledEvent(1.0, 0, null, NO_OP));
        assertThrows(NullPointerException.class, () -> new ScheduledEvent(1.0, 0, "x", null));
    }
}