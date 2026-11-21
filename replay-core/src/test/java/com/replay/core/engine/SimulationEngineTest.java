package com.replay.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SimulationEngineTest {

    private static final EventAction NO_OP = ctx -> { };

    private SimulationEngine engine;
    private List<String> log;

    @BeforeEach
    void setUp() {
        engine = new SimulationEngine();
        log = new ArrayList<>();
    }

    /** Schedules an event that appends its name to the log when it runs. */
    private void logAt(double time, String name) {
        engine.scheduleAt(time, name, ctx -> log.add(name));
    }

    // ---- ordering ----

    @Test
    void executesEventsInChronologicalOrder() {
        logAt(3.0, "c");
        logAt(1.0, "a");
        logAt(2.0, "b");
        engine.run();
        assertEquals(List.of("a", "b", "c"), log);
    }

    @Test
    void eventsWithIdenticalTimestampsRunInInsertionOrder() {
        logAt(5.0, "first");
        logAt(5.0, "second");
        logAt(5.0, "third");
        engine.run();
        assertEquals(List.of("first", "second", "third"), log);
    }

    @Test
    void eventCanScheduleAnotherEvent() {
        engine.scheduleAt(1.0, "parent", ctx -> {
            log.add("parent");
            ctx.scheduleAfter(2.0, "child", c -> log.add("child"));
        });
        engine.run();
        assertEquals(List.of("parent", "child"), log);
        assertEquals(3.0, engine.now());
    }

    @Test
    void eventsScheduledDuringRunAreOrderedCorrectly() {
        engine.scheduleAt(1.0, "A", ctx -> {
            log.add("A");
            ctx.scheduleAt(2.0, "A-child", c -> log.add("A-child"));
        });
        logAt(1.5, "B");
        logAt(3.0, "C");
        engine.run();
        assertEquals(List.of("A", "B", "A-child", "C"), log);
    }

    @Test
    void eventScheduledForCurrentTimeRunsAfterExistingEventsAtThatTime() {
        engine.scheduleAt(1.0, "A", ctx -> {
            log.add("A");
            ctx.scheduleAfter(0.0, "A-child", c -> log.add("A-child"));
        });
        logAt(1.0, "B");
        engine.run();
        assertEquals(List.of("A", "B", "A-child"), log);
    }

    // ---- clock ----

    @Test
    void clockStartsAtZeroAndMatchesEventTimeDuringExecution() {
        assertEquals(0.0, engine.now());
        List<Double> seen = new ArrayList<>();
        engine.scheduleAt(1.0, "a", ctx -> seen.add(ctx.now()));
        engine.scheduleAt(2.5, "b", ctx -> seen.add(ctx.now()));
        engine.run();
        assertEquals(List.of(1.0, 2.5), seen);
        assertEquals(2.5, engine.now());
    }

    @Test
    void runOnEmptyEngineDoesNothing() {
        engine.run();
        assertEquals(0.0, engine.now());
        assertEquals(0, engine.processedEventCount());
    }

    // ---- runUntil ----

    @Test
    void runUntilStopsAtRequestedTimeAndKeepsLaterEventsPending() {
        logAt(1.0, "a");
        logAt(5.0, "b");
        logAt(9.0, "c");
        engine.runUntil(6.0);
        assertEquals(List.of("a", "b"), log);
        assertEquals(1, engine.pendingEventCount());
        assertEquals(6.0, engine.now());
    }

    @Test
    void runUntilIsInclusiveOfEventsAtExactlyTheEndTime() {
        logAt(5.0, "on-boundary");
        logAt(5.1, "after");
        engine.runUntil(5.0);
        assertEquals(List.of("on-boundary"), log);
        assertEquals(1, engine.pendingEventCount());
    }

    @Test
    void pendingEventsAreProcessedByLaterRun() {
        logAt(1.0, "a");
        logAt(5.0, "b");
        logAt(9.0, "c");
        engine.runUntil(6.0);
        engine.run();
        assertEquals(List.of("a", "b", "c"), log);
        assertEquals(0, engine.pendingEventCount());
        assertEquals(9.0, engine.now());
    }

    @Test
    void runUntilProcessesEventsScheduledDuringTheRunIfWithinLimit() {
        engine.scheduleAt(1.0, "parent", ctx -> {
            log.add("parent");
            ctx.scheduleAt(4.0, "in-range", c -> log.add("in-range"));
            ctx.scheduleAt(8.0, "out-of-range", c -> log.add("out-of-range"));
        });
        engine.runUntil(5.0);
        assertEquals(List.of("parent", "in-range"), log);
        assertEquals(1, engine.pendingEventCount());
    }

    @Test
    void runUntilRejectsEndTimeInThePast() {
        engine.runUntil(10.0);
        assertThrows(IllegalArgumentException.class, () -> engine.runUntil(5.0));
        assertEquals(10.0, engine.now());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void runUntilRejectsInvalidEndTime(double badTime) {
        assertThrows(IllegalArgumentException.class, () -> engine.runUntil(badTime));
    }

    // ---- validation ----

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void scheduleAtRejectsInvalidTimes(double badTime) {
        assertThrows(IllegalArgumentException.class, () -> engine.scheduleAt(badTime, "x", NO_OP));
        assertEquals(0, engine.pendingEventCount());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void scheduleAfterRejectsInvalidDelays(double badDelay) {
        assertThrows(IllegalArgumentException.class, () -> engine.scheduleAfter(badDelay, "x", NO_OP));
        assertEquals(0, engine.pendingEventCount());
    }

    @Test
    void rejectsSchedulingInThePast() {
        engine.runUntil(10.0);
        assertThrows(IllegalArgumentException.class, () -> engine.scheduleAt(5.0, "late", NO_OP));
        assertEquals(0, engine.pendingEventCount());
    }

    @Test
    void rejectsSchedulingInThePastFromInsideAnEvent() {
        engine.scheduleAt(5.0, "bad-event", ctx -> ctx.scheduleAt(3.0, "too-late", NO_OP));
        assertThrows(IllegalArgumentException.class, engine::run);
    }

    @Test
    void rejectsNullDescriptionAndAction() {
        assertThrows(NullPointerException.class, () -> engine.scheduleAt(1.0, null, NO_OP));
        assertThrows(NullPointerException.class, () -> engine.scheduleAt(1.0, "x", null));
        assertThrows(NullPointerException.class, () -> engine.scheduleAfter(1.0, null, NO_OP));
        assertThrows(NullPointerException.class, () -> engine.scheduleAfter(1.0, "x", null));
        assertEquals(0, engine.pendingEventCount());
    }

    @Test
    void eventCanBeScheduledAtExactlyTheCurrentTime() {
        engine.runUntil(4.0);
        logAt(4.0, "now");
        engine.run();
        assertEquals(List.of("now"), log);
    }

    // ---- re-entrancy ----

    @Test
    void rejectsReentrantRun() {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        engine.scheduleAt(1.0, "reenter", ctx -> {
            try {
                engine.run();
            } catch (IllegalStateException e) {
                caught.set(e);
            }
        });
        engine.run();
        assertInstanceOf(IllegalStateException.class, caught.get());
    }

    @Test
    void rejectsReentrantRunUntil() {
        AtomicReference<Throwable> caught = new AtomicReference<>();
        engine.scheduleAt(1.0, "reenter", ctx -> {
            try {
                engine.runUntil(100.0);
            } catch (IllegalStateException e) {
                caught.set(e);
            }
        });
        engine.run();
        assertInstanceOf(IllegalStateException.class, caught.get());
    }

    // ---- counts and failure behaviour ----

    @Test
    void countsProcessedEventsIncludingOnesScheduledDuringRun() {
        engine.scheduleAt(1.0, "parent", ctx -> ctx.scheduleAfter(1.0, "child", NO_OP));
        engine.scheduleAt(5.0, "later", NO_OP);
        assertEquals(0, engine.processedEventCount());

        engine.runUntil(3.0);
        assertEquals(2, engine.processedEventCount()); // parent + child
        assertEquals(1, engine.pendingEventCount());

        engine.run();
        assertEquals(3, engine.processedEventCount());
        assertEquals(0, engine.pendingEventCount());
    }

    @Test
    void failingEventPropagatesKeepsOthersPendingAndEngineStaysUsable() {
        engine.scheduleAt(1.0, "boom", ctx -> {
            throw new IllegalStateException("boom");
        });
        logAt(2.0, "after");

        assertThrows(IllegalStateException.class, engine::run);
        assertEquals(0, engine.processedEventCount()); // failed event is not counted
        assertEquals(1, engine.pendingEventCount());
        assertEquals(1.0, engine.now());

        engine.run(); // running flag was reset, so this is allowed
        assertEquals(List.of("after"), log);
        assertEquals(1, engine.processedEventCount());
    }
}