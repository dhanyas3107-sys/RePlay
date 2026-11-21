package com.replay.cli;

import com.replay.core.metrics.DeskMetrics;
import com.replay.core.metrics.MetricsCalculator;
import com.replay.core.metrics.SimulationMetrics;
import com.replay.core.model.Attendee;
import com.replay.core.model.ServiceDesk;
import com.replay.core.operations.EventSimulation;
import java.util.List;
import java.util.Locale;

/**
 * Runs the same registration scenario as EventSimulationDemo, then prints the metrics
 * calculated from its event log. Rounding happens only here, in the output formatting.
 */
public final class MetricsDemo {

    private MetricsDemo() {
    }

    public static void main(String[] args) {
        List<Attendee> attendees = List.of(
                new Attendee("A1", 0.0),
                new Attendee("A2", 1.0),
                new Attendee("A3", 2.0),
                new Attendee("A4", 3.0),
                new Attendee("A5", 7.0));
        List<ServiceDesk> desks = List.of(
                new ServiceDesk("D1", 4.0),
                new ServiceDesk("D2", 6.0));

        EventSimulation simulation = new EventSimulation(attendees, desks);
        simulation.run();
        SimulationMetrics m = MetricsCalculator.calculate(simulation);

        System.out.println("RePlay metrics: 5 attendees, 2 desks (D1 = 4.0 min, D2 = 6.0 min)");
        System.out.println();
        System.out.println("Overall");
        System.out.printf(Locale.ROOT, "  Total attendees: %d%n", m.totalAttendees());
        System.out.printf(Locale.ROOT, "  Completed attendees: %d%n", m.completedAttendees());
        System.out.printf(Locale.ROOT, "  Average waiting time: %.2f min%n", m.averageWaitingTime());
        System.out.printf(Locale.ROOT, "  Maximum waiting time: %.2f min%n", m.maximumWaitingTime());
        System.out.printf(Locale.ROOT, "  Average time in system: %.2f min%n", m.averageTimeInSystem());
        System.out.printf(Locale.ROOT, "  Maximum queue length: %d%n", m.maximumQueueLength());
        System.out.printf(Locale.ROOT, "  Observation window: t=%.1f to t=%.1f (%.2f min)%n",
                m.observationStart(), m.observationEnd(), m.totalSimulationDuration());
        System.out.printf(Locale.ROOT, "  Throughput: %.3f attendees/min%n", m.throughputPerMinute());
        System.out.printf(Locale.ROOT, "  Overall desk utilisation: %.1f%%%n", m.overallUtilisationPercent());

        System.out.println();
        System.out.println("Desks");
        System.out.println(String.format(Locale.ROOT, "  %-6s%8s%12s%14s", "Desk", "Served", "Busy (min)", "Utilisation"));
        for (DeskMetrics d : m.deskMetrics()) {
            System.out.println(String.format(Locale.ROOT, "  %-6s%8d%12.2f%13.1f%%",
                    d.deskId(), d.attendeesServed(), d.totalBusyTime(), d.utilisationPercent()));
        }
    }
}