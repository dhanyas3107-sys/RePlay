package com.replay.cli;

import com.replay.core.model.Attendee;
import com.replay.core.model.ServiceDesk;
import com.replay.core.operations.EventSimulation;
import java.util.List;
import java.util.Locale;

/**
 * Demo of the deterministic registration model: 5 attendees, 2 desks with
 * different fixed service times. Not the real CLI; that comes in a later phase.
 */
public final class EventSimulationDemo {

    private EventSimulationDemo() {
    }

    public static void main(String[] args) {
        List<Attendee> attendees = List.of(
                new Attendee("A1", 0.0),
                new Attendee("A2", 1.0),
                new Attendee("A3", 2.0),
                new Attendee("A4", 3.0),
                new Attendee("A5", 7.0)); // arrives at the exact moment D2 finishes A2

        List<ServiceDesk> desks = List.of(
                new ServiceDesk("D1", 4.0),
                new ServiceDesk("D2", 6.0));

        System.out.println("Registration simulation: 5 attendees, 2 desks (D1 = 4.0 min, D2 = 6.0 min)");
        System.out.println();

        EventSimulation simulation = new EventSimulation(attendees, desks);
        simulation.run();
        simulation.log().forEach(System.out::println);

        System.out.println();
        System.out.printf(Locale.ROOT, "Finished at t=%.1f after %d events.%n",
                simulation.now(), simulation.processedEventCount());
    }
}