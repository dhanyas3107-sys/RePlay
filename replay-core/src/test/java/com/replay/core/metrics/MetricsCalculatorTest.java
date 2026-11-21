package com.replay.core.metrics;

import static com.replay.core.operations.SimulationLogEntry.Type.ARRIVAL;
import static com.replay.core.operations.SimulationLogEntry.Type.QUEUED;
import static com.replay.core.operations.SimulationLogEntry.Type.SERVICE_END;
import static com.replay.core.operations.SimulationLogEntry.Type.SERVICE_START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.replay.core.model.Attendee;
import com.replay.core.model.ServiceDesk;
import com.replay.core.operations.EventSimulation;
import com.replay.core.operations.SimulationLogEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetricsCalculatorTest {

    private static final double EPS = 1e-9;

    private static Attendee att(String id, double arrival) {
        return new Attendee(id, arrival);
    }

    private static ServiceDesk desk(String id, double duration) {
        return new ServiceDesk(id, duration);
    }

    private static SimulationLogEntry e(double t, SimulationLogEntry.Type type, String attendee, String desk) {
        return new SimulationLogEntry(t, type, attendee, desk);
    }

    private static SimulationMetrics metricsFor(List<Attendee> attendees, List<ServiceDesk> desks) {
        EventSimulation sim = new EventSimulation(attendees, desks);
        sim.run();
        return MetricsCalculator.calculate(sim);
    }

    private static void assertAllFinite(SimulationMetrics m) {
        double[] values = {m.averageWaitingTime(), m.maximumWaitingTime(), m.averageTimeInSystem(),
                m.observationStart(), m.observationEnd(), m.totalSimulationDuration(),
                m.throughputPerMinute(), m.overallUtilisationPercent()};
        for (double v : values) {
            assertTrue(Double.isFinite(v), "Non-finite metric: " + v);
        }
        for (DeskMetrics d : m.deskMetrics()) {
            assertTrue(Double.isFinite(d.totalBusyTime()) && Double.isFinite(d.utilisationPercent()));
        }
    }

    // ---- hand-calculated scenarios on real simulations ----

    @Test
    void singleDeskWithQueueGivesHandCalculatedWaitsAndSystemTimes() {
        // Desk takes 4 min. A1: 0->4 (wait 0). A2 arrives 1, served 4->8 (wait 3). A3 arrives 2, served 8->12 (wait 6).
        SimulationMetrics m = metricsFor(
                List.of(att("A1", 0.0), att("A2", 1.0), att("A3", 2.0)), List.of(desk("D1", 4.0)));

        assertEquals(3, m.totalAttendees());
        assertEquals(3, m.completedAttendees());
        assertEquals(3.0, m.averageWaitingTime(), EPS);       // (0+3+6)/3
        assertEquals(6.0, m.maximumWaitingTime(), EPS);
        assertEquals(7.0, m.averageTimeInSystem(), EPS);      // (4+7+10)/3
        assertEquals(2, m.maximumQueueLength());              // A2 and A3 waiting together
        assertEquals(0.0, m.observationStart(), EPS);
        assertEquals(12.0, m.observationEnd(), EPS);
        assertEquals(12.0, m.totalSimulationDuration(), EPS);
        assertEquals(0.25, m.throughputPerMinute(), EPS);     // 3 / 12
        assertEquals(100.0, m.overallUtilisationPercent(), EPS);

        DeskMetrics d1 = m.deskMetrics().get(0);
        assertEquals("D1", d1.deskId());
        assertEquals(3, d1.attendeesServed());
        assertEquals(12.0, d1.totalBusyTime(), EPS);
        assertEquals(100.0, d1.utilisationPercent(), EPS);
        assertAllFinite(m);
    }

    @Test
    void simultaneousArrivalsBuildTheLongestQueue() {
        // One desk, 10 min each, four attendees at t=0: waits 0,10,20,30; time in system 10,20,30,40.
        SimulationMetrics m = metricsFor(
                List.of(att("A1", 0.0), att("A2", 0.0), att("A3", 0.0), att("A4", 0.0)),
                List.of(desk("D1", 10.0)));

        assertEquals(3, m.maximumQueueLength()); // A1 is served at once; A2..A4 queue
        assertEquals(15.0, m.averageWaitingTime(), EPS);
        assertEquals(30.0, m.maximumWaitingTime(), EPS);
        assertEquals(25.0, m.averageTimeInSystem(), EPS);
        assertEquals(40.0, m.totalSimulationDuration(), EPS);
        assertEquals(0.1, m.throughputPerMinute(), EPS);
    }

    @Test
    void phase2DemoScenarioMatchesHandCalculation() {
        // Same scenario as EventSimulationDemo: D1 = 4 min, D2 = 6 min.
        // A1 wait 0/sys 4, A2 wait 0/sys 6, A3 wait 2/sys 6, A4 wait 4/sys 10, A5 wait 1/sys 5.
        SimulationMetrics m = metricsFor(
                List.of(att("A1", 0.0), att("A2", 1.0), att("A3", 2.0), att("A4", 3.0), att("A5", 7.0)),
                List.of(desk("D1", 4.0), desk("D2", 6.0)));

        assertEquals(5, m.totalAttendees());
        assertEquals(5, m.completedAttendees());
        assertEquals(1.4, m.averageWaitingTime(), EPS);       // 7 / 5
        assertEquals(4.0, m.maximumWaitingTime(), EPS);
        assertEquals(6.2, m.averageTimeInSystem(), EPS);      // 31 / 5
        assertEquals(2, m.maximumQueueLength());              // A3 and A4 together during t=[3,4)
        assertEquals(0.0, m.observationStart(), EPS);
        assertEquals(13.0, m.observationEnd(), EPS);
        assertEquals(5.0 / 13.0, m.throughputPerMinute(), EPS);
        assertEquals(24.0 / 26.0 * 100.0, m.overallUtilisationPercent(), EPS);

        DeskMetrics d1 = m.deskMetrics().get(0);
        DeskMetrics d2 = m.deskMetrics().get(1);
        assertEquals("D1", d1.deskId());
        assertEquals(3, d1.attendeesServed());   // A1, A3, A5
        assertEquals(12.0, d1.totalBusyTime(), EPS);
        assertEquals(12.0 / 13.0 * 100.0, d1.utilisationPercent(), EPS);
        assertEquals("D2", d2.deskId());
        assertEquals(2, d2.attendeesServed());   // A2, A4
        assertEquals(12.0, d2.totalBusyTime(), EPS);
        assertEquals(12.0 / 13.0 * 100.0, d2.utilisationPercent(), EPS);
        assertAllFinite(m);
    }

    @Test
    void desksWithDifferentDurationsHaveBusyTimeEqualToServedTimesDuration() {
        SimulationMetrics m = metricsFor(
                List.of(att("A1", 0.0), att("A2", 1.0), att("A3", 2.0), att("A4", 3.0), att("A5", 7.0)),
                List.of(desk("D1", 4.0), desk("D2", 6.0)));

        assertEquals(3 * 4.0, m.deskMetrics().get(0).totalBusyTime(), EPS);
        assertEquals(2 * 6.0, m.deskMetrics().get(1).totalBusyTime(), EPS);
        int servedTotal = m.deskMetrics().stream().mapToInt(DeskMetrics::attendeesServed).sum();
        assertEquals(m.completedAttendees(), servedTotal);
        for (DeskMetrics d : m.deskMetrics()) {
            assertTrue(d.utilisationPercent() <= 100.0 + EPS);
        }
        assertTrue(m.overallUtilisationPercent() <= 100.0 + EPS);
    }

    @Test
    void idleDeskIsReportedWithZeroUtilisationAndLowersTheOverallFigure() {
        // A1 goes to D1 (first in list). D2 never works. Window = 0..5.
        SimulationMetrics m = metricsFor(List.of(att("A1", 0.0)), List.of(desk("D1", 5.0), desk("D2", 3.0)));

        assertEquals(2, m.deskMetrics().size());
        assertEquals(100.0, m.deskMetrics().get(0).utilisationPercent(), EPS);
        DeskMetrics idle = m.deskMetrics().get(1);
        assertEquals("D2", idle.deskId());
        assertEquals(0, idle.attendeesServed());
        assertEquals(0.0, idle.totalBusyTime(), EPS);
        assertEquals(0.0, idle.utilisationPercent(), EPS);
        assertEquals(50.0, m.overallUtilisationPercent(), EPS); // 5 / (2 * 5)
    }

    @Test
    void observationWindowStartsAtFirstArrivalNotAtZero() {
        SimulationMetrics m = metricsFor(List.of(att("A1", 10.0)), List.of(desk("D1", 5.0)));

        assertEquals(10.0, m.observationStart(), EPS);
        assertEquals(15.0, m.observationEnd(), EPS);
        assertEquals(5.0, m.totalSimulationDuration(), EPS);
        assertEquals(0.2, m.throughputPerMinute(), EPS);
        assertEquals(100.0, m.deskMetrics().get(0).utilisationPercent(), EPS);
    }

    // ---- the same-timestamp queue-length rule (decision D12) ----

    @Test
    void attendeeWhoArrivesAtTheMomentADeskFreesDoesNotCountAsQueued() {
        // Phase 2 logs QUEUED then SERVICE_START for A2 at t=5, but A2 waits 0 minutes.
        SimulationMetrics m = metricsFor(
                List.of(att("A1", 0.0), att("A2", 5.0)), List.of(desk("D1", 5.0)));

        assertEquals(0, m.maximumQueueLength());
        assertEquals(0.0, m.averageWaitingTime(), EPS);
        assertEquals(0.0, m.maximumWaitingTime(), EPS);
        assertEquals(5.0, m.averageTimeInSystem(), EPS);
    }

    @Test
    void queueLengthIsMeasuredAfterAllEventsOfAnInstantInARealSimulation() {
        // One desk, 5 min each. A2 waits from t=1 until t=5. A3 arrives at t=5, exactly when A1 finishes.
        // All at t=5 the log reads: ARRIVAL A3, QUEUED A3, SERVICE_END A1, SERVICE_START A2.
        // Counting after every single entry would see {A2, A3} = 2 for a moment that lasts zero time.
        // The real queue is {A2} during [1,5) and {A3} during [5,10), so the maximum is 1.
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0), att("A2", 1.0), att("A3", 5.0)), List.of(desk("D1", 5.0)));
        sim.run();
        assertTrue(sim.log().contains(e(5.0, QUEUED, "A3", null))); // precondition: the tie really occurs

        SimulationMetrics m = MetricsCalculator.calculate(sim);
        assertEquals(1, m.maximumQueueLength());
        assertEquals(3.0, m.averageWaitingTime(), EPS); // waits 0, 4, 5
    }

    @Test
    void queueLengthIsMeasuredAfterAllEntriesOfAnInstantInAHandBuiltLog() {
        List<SimulationLogEntry> log = List.of(
                e(0.0, ARRIVAL, "A1", null),
                e(0.0, SERVICE_START, "A1", "D1"),
                e(1.0, ARRIVAL, "A2", null),
                e(1.0, QUEUED, "A2", null),          // queue = {A2}
                e(5.0, ARRIVAL, "A3", null),
                e(5.0, QUEUED, "A3", null),          // momentarily {A2, A3}
                e(5.0, SERVICE_END, "A1", "D1"),
                e(5.0, SERVICE_START, "A2", "D1"));  // end of instant: {A3}
        assertEquals(1, MetricsCalculator.calculate(log, List.of("D1")).maximumQueueLength());
    }

    @Test
    void genuineQueueOfTwoThatLastsPositiveTimeIsStillCounted() {
        List<SimulationLogEntry> log = List.of(
                e(0.0, ARRIVAL, "A1", null),
                e(0.0, SERVICE_START, "A1", "D1"),
                e(1.0, ARRIVAL, "A2", null),
                e(1.0, QUEUED, "A2", null),
                e(2.0, ARRIVAL, "A3", null),
                e(2.0, QUEUED, "A3", null),          // {A2, A3} from t=2 ...
                e(4.0, SERVICE_END, "A1", "D1"),     // ... until t=4
                e(4.0, SERVICE_START, "A2", "D1"));
        assertEquals(2, MetricsCalculator.calculate(log, List.of("D1")).maximumQueueLength());
    }

    // ---- empty and degenerate input ----

    @Test
    void emptyAttendeeListGivesZeroMetricsWithoutInvalidNumbers() {
        SimulationMetrics m = metricsFor(List.of(), List.of(desk("D1", 3.0), desk("D2", 4.0)));

        assertEquals(0, m.totalAttendees());
        assertEquals(0, m.completedAttendees());
        assertEquals(0.0, m.averageWaitingTime());
        assertEquals(0.0, m.maximumWaitingTime());
        assertEquals(0.0, m.averageTimeInSystem());
        assertEquals(0, m.maximumQueueLength());
        assertEquals(0.0, m.totalSimulationDuration());
        assertEquals(0.0, m.throughputPerMinute());
        assertEquals(0.0, m.overallUtilisationPercent());
        assertEquals(2, m.deskMetrics().size());
        for (DeskMetrics d : m.deskMetrics()) {
            assertEquals(0, d.attendeesServed());
            assertEquals(0.0, d.utilisationPercent());
        }
        assertAllFinite(m);
    }

    @Test
    void logWithUnfinishedServiceHasZeroWindowAndSafeValues() {
        List<SimulationLogEntry> log = List.of(
                e(0.0, ARRIVAL, "A1", null),
                e(0.0, SERVICE_START, "A1", "D1"));
        SimulationMetrics m = MetricsCalculator.calculate(log, List.of("D1"));

        assertEquals(1, m.totalAttendees());
        assertEquals(0, m.completedAttendees());
        assertEquals(0.0, m.averageWaitingTime());
        assertEquals(0.0, m.totalSimulationDuration());
        assertEquals(0.0, m.throughputPerMinute());
        assertEquals(0.0, m.deskMetrics().get(0).totalBusyTime()); // open service is not counted
        assertEquals(0.0, m.deskMetrics().get(0).utilisationPercent());
        assertAllFinite(m);
    }

    @Test
    void logWithOnlyArrivalsAndQueueingHasZeroWindowButCountsTheQueue() {
        List<SimulationLogEntry> log = List.of(
                e(3.0, ARRIVAL, "A1", null),
                e(3.0, QUEUED, "A1", null));
        SimulationMetrics m = MetricsCalculator.calculate(log, List.of("D1"));

        assertEquals(1, m.totalAttendees());
        assertEquals(0, m.completedAttendees());
        assertEquals(1, m.maximumQueueLength());
        assertEquals(3.0, m.observationStart());
        assertEquals(3.0, m.observationEnd());
        assertEquals(0.0, m.throughputPerMinute());
        assertEquals(0.0, m.overallUtilisationPercent());
        assertAllFinite(m);
    }

    @Test
    void emptyLogIsAccepted() {
        SimulationMetrics m = MetricsCalculator.calculate(List.of(), List.of("D1"));
        assertEquals(0, m.totalAttendees());
        assertAllFinite(m);
    }

    // ---- invalid input ----

    @Test
    void rejectsInconsistentLogs() {
        List<String> d1 = List.of("D1");
        // service ends without ever starting
        assertThrows(IllegalArgumentException.class, () -> MetricsCalculator.calculate(
                List.of(e(0.0, ARRIVAL, "A1", null), e(4.0, SERVICE_END, "A1", "D1")), d1));
        // queued without arriving
        assertThrows(IllegalArgumentException.class, () -> MetricsCalculator.calculate(
                List.of(e(0.0, QUEUED, "A1", null)), d1));
        // same attendee arrives twice
        assertThrows(IllegalArgumentException.class, () -> MetricsCalculator.calculate(
                List.of(e(0.0, ARRIVAL, "A1", null), e(1.0, ARRIVAL, "A1", null)), d1));
        // desk starts a second service while busy
        assertThrows(IllegalArgumentException.class, () -> MetricsCalculator.calculate(
                List.of(e(0.0, ARRIVAL, "A1", null), e(0.0, ARRIVAL, "A2", null),
                        e(0.0, SERVICE_START, "A1", "D1"), e(0.0, SERVICE_START, "A2", "D1")), d1));
        // service ends at a different desk than it started
        assertThrows(IllegalArgumentException.class, () -> MetricsCalculator.calculate(
                List.of(e(0.0, ARRIVAL, "A1", null), e(0.0, SERVICE_START, "A1", "D1"),
                        e(4.0, SERVICE_END, "A1", "D2")), List.of("D1", "D2")));
        // desk not in the supplied list
        assertThrows(IllegalArgumentException.class, () -> MetricsCalculator.calculate(
                List.of(e(0.0, ARRIVAL, "A1", null), e(0.0, SERVICE_START, "A1", "DX")), d1));
        // time goes backwards
        assertThrows(IllegalArgumentException.class, () -> MetricsCalculator.calculate(
                List.of(e(5.0, ARRIVAL, "A1", null), e(3.0, ARRIVAL, "A2", null)), d1));
    }

    @Test
    void rejectsDuplicateDeskIdsAndNulls() {
        assertThrows(IllegalArgumentException.class, () -> MetricsCalculator.calculate(List.of(), List.of("D1", "D1")));
        assertThrows(NullPointerException.class, () -> MetricsCalculator.calculate(null, List.of("D1")));
        assertThrows(NullPointerException.class, () -> MetricsCalculator.calculate(List.of(), null));
        assertThrows(NullPointerException.class, () -> MetricsCalculator.calculate((EventSimulation) null));
    }

    @Test
    void rejectsSimulationThatHasNotBeenRun() {
        EventSimulation sim = new EventSimulation(List.of(att("A1", 0.0)), List.of(desk("D1", 1.0)));
        assertThrows(IllegalStateException.class, () -> MetricsCalculator.calculate(sim));
    }

    // ---- the two accessors added to EventSimulation ----

    @Test
    void eventSimulationExposesDeskIdsInSuppliedOrderAndRunState() {
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0)), List.of(desk("D2", 1.0), desk("D1", 1.0)));
        assertEquals(List.of("D2", "D1"), sim.deskIds());
        assertFalse(sim.hasRun());
        sim.run();
        assertTrue(sim.hasRun());
    }

    // ---- immutability ----

    @Test
    void deskMetricsListCannotBeModified() {
        SimulationMetrics m = metricsFor(List.of(att("A1", 0.0)), List.of(desk("D1", 1.0)));
        assertThrows(UnsupportedOperationException.class, () -> m.deskMetrics().add(new DeskMetrics("X", 0, 0.0, 0.0)));
    }
}