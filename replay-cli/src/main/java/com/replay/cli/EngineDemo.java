package com.replay.cli;

import com.replay.core.engine.SimulationEngine;
import java.util.Locale;

/**
 * Tiny demo of the DES engine: events scheduling events, and pausing and
 * resuming with runUntil(). It is not the real CLI; that comes in Phase 7a.
 */
public final class EngineDemo {

    private EngineDemo() {
    }

    public static void main(String[] args) {
        SimulationEngine engine = new SimulationEngine();

        // "Gates open" at t=0 schedules the first arrival 5 minutes later,
        // and that arrival schedules a departure 10 minutes after itself.
        engine.scheduleAt(0.0, "Gates open", ctx -> {
            System.out.println(line(ctx.now(), "Gates open"));
            ctx.scheduleAfter(5.0, "First attendee arrives", c -> {
                System.out.println(line(c.now(), "First attendee arrives"));
                c.scheduleAfter(10.0, "Attendee leaves", c2 ->
                        System.out.println(line(c2.now(), "Attendee leaves")));
            });
        });
        engine.scheduleAt(12.0, "Lunch break", ctx ->
                System.out.println(line(ctx.now(), "Lunch break")));

        engine.runUntil(10.0);
        System.out.printf(Locale.ROOT, "-- paused at t=%.1f, pending=%d --%n",
                engine.now(), engine.pendingEventCount());

        engine.run();
        System.out.printf(Locale.ROOT, "-- finished at t=%.1f, processed=%d --%n",
                engine.now(), engine.processedEventCount());
    }

    private static String line(double time, String text) {
        return String.format(Locale.ROOT, "[t=%5.1f] %s", time, text);
    }
}