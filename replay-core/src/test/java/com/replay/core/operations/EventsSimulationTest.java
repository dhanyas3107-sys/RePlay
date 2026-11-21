package com.replay.core.operations;

import static com.replay.core.operations.SimulationLogEntry.Type.ARRIVAL;
import static com.replay.core.operations.SimulationLogEntry.Type.QUEUED;
import static com.replay.core.operations.SimulationLogEntry.Type.SERVICE_END;
import static com.replay.core.operations.SimulationLogEntry.Type.SERVICE_START;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.replay.core.model.Attendee;
import com.replay.core.model.ServiceDesk;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventSimulationTest {

    private static Attendee att(String id, double arrival) {
        return new Attendee(id, arrival);
    }

    private static ServiceDesk desk(String id, double duration) {
        return new ServiceDesk(id, duration);
    }

    private static SimulationLogEntry e(double time, SimulationLogEntry.Type type, String attendee, String desk) {
        return new SimulationLogEntry(time, type, attendee, desk);
    }

    /** "A1@0.0"-style summary of every SERVICE_START, in log order. */
    private static List<String> serviceStarts(EventSimulation sim) {
        return sim.log().stream()
                .filter(entry -> entry.type() == SERVICE_START)
                .map(entry -> entry.attendeeId() + "@" + entry.time())
                .toList();
    }

    // ---- basic flow ----

    @Test
    void attendeeArrivingWhenDeskIsAvailableIsServedImmediately() {
        EventSimulation sim = new EventSimulation(List.of(att("A1", 2.0)), List.of(desk("D1", 3.0)));
        sim.run();
        assertEquals(List.of(
                e(2.0, ARRIVAL, "A1", null),
                e(2.0, SERVICE_START, "A1", "D1"),
                e(5.0, SERVICE_END, "A1", "D1")), sim.log());
        assertEquals(5.0, sim.now());
        assertEquals(3, sim.processedEventCount() + 1); // 2 engine events (arrival, end) + 1
    }

    @Test
    void attendeeWaitsWhenAllDesksAreOccupied() {
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0), att("A2", 1.0)), List.of(desk("D1", 5.0)));
        sim.run();
        assertEquals(List.of(
                e(0.0, ARRIVAL, "A1", null),
                e(0.0, SERVICE_START, "A1", "D1"),
                e(1.0, ARRIVAL, "A2", null),
                e(1.0, QUEUED, "A2", null),
                e(5.0, SERVICE_END, "A1", "D1"),
                e(5.0, SERVICE_START, "A2", "D1"),
                e(10.0, SERVICE_END, "A2", "D1")), sim.log());
    }

    @Test
    void attendeesAreServedInFifoOrder() {
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0), att("A2", 1.0), att("A3", 2.0), att("A4", 3.0)),
                List.of(desk("D1", 2.0)));
        sim.run();
        assertEquals(List.of("A1@0.0", "A2@2.0", "A3@4.0", "A4@6.0"), serviceStarts(sim));
    }

    @Test
    void deskBecomesAvailableAfterServiceCompletes() {
        ServiceDesk d1 = desk("D1", 2.0);
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0), att("A2", 1.0)), List.of(d1));
        sim.run();
        assertTrue(d1.isAvailable());
        assertEquals(0, sim.waitingCount());
        assertEquals(4.0, sim.now()); // A2 starts at 2, ends at 4
    }

    // ---- multiple desks ----

    @Test
    void multipleDesksServeAttendeesSimultaneously() {
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0), att("A2", 0.0)),
                List.of(desk("D1", 4.0), desk("D2", 4.0)));
        sim.run();
        assertEquals(List.of(
                e(0.0, ARRIVAL, "A1", null),
                e(0.0, SERVICE_START, "A1", "D1"), // first free desk in list order
                e(0.0, ARRIVAL, "A2", null),
                e(0.0, SERVICE_START, "A2", "D2"), // overlaps with A1's service
                e(4.0, SERVICE_END, "A1", "D1"),
                e(4.0, SERVICE_END, "A2", "D2")), sim.log());
    }

    @Test
    void twoDesksShareOneQueueAndSimultaneousCompletionsAreOrderedByServiceStart() {
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0), att("A2", 0.0), att("A3", 1.0), att("A4", 1.0)),
                List.of(desk("D1", 4.0), desk("D2", 4.0)));
        sim.run();
        assertEquals(List.of(
                e(0.0, ARRIVAL, "A1", null),
                e(0.0, SERVICE_START, "A1", "D1"),
                e(0.0, ARRIVAL, "A2", null),
                e(0.0, SERVICE_START, "A2", "D2"),
                e(1.0, ARRIVAL, "A3", null),
                e(1.0, QUEUED, "A3", null),
                e(1.0, ARRIVAL, "A4", null),
                e(1.0, QUEUED, "A4", null),
                e(4.0, SERVICE_END, "A1", "D1"),
                e(4.0, SERVICE_START, "A3", "D1"),
                e(4.0, SERVICE_END, "A2", "D2"),
                e(4.0, SERVICE_START, "A4", "D2"),
                e(8.0, SERVICE_END, "A3", "D1"),
                e(8.0, SERVICE_END, "A4", "D2")), sim.log());
    }

    // ---- simultaneous events ----

    @Test
    void simultaneousArrivalsAreProcessedInListOrder() {
        EventSimulation sim = new EventSimulation(
                List.of(att("B", 0.0), att("A", 0.0), att("C", 0.0)),
                List.of(desk("D1", 1.0)));
        sim.run();
        assertEquals(List.of("B@0.0", "A@1.0", "C@2.0"), serviceStarts(sim));
    }

    @Test
    void arrivalAtTheSameTimeAsCompletionIsProcessedFirstThenServedAtThatInstant() {
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0), att("A2", 5.0)), List.of(desk("D1", 5.0)));
        sim.run();
        assertEquals(List.of(
                e(0.0, ARRIVAL, "A1", null),
                e(0.0, SERVICE_START, "A1", "D1"),
                e(5.0, ARRIVAL, "A2", null),     // arrival is processed before the completion...
                e(5.0, QUEUED, "A2", null),      // ...so the desk still looks busy
                e(5.0, SERVICE_END, "A1", "D1"),
                e(5.0, SERVICE_START, "A2", "D1"), // but A2 is served at the same instant (0 wait)
                e(10.0, SERVICE_END, "A2", "D1")), sim.log());
    }

    @Test
    void arrivalAtCompletionTimeDoesNotJumpAheadOfAttendeesAlreadyWaiting() {
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0), att("A2", 1.0), att("A3", 5.0)),
                List.of(desk("D1", 5.0)));
        sim.run();
        assertEquals(List.of("A1@0.0", "A2@5.0", "A3@10.0"), serviceStarts(sim));
    }

    // ---- full hand-traced scenario (the same one the demo prints) ----

    @Test
    void demoScenarioMatchesHandTrace() {
        EventSimulation sim = new EventSimulation(
                List.of(att("A1", 0.0), att("A2", 1.0), att("A3", 2.0), att("A4", 3.0), att("A5", 7.0)),
                List.of(desk("D1", 4.0), desk("D2", 6.0)));
        sim.run();
        assertEquals(List.of(
                e(0.0, ARRIVAL, "A1", null),
                e(0.0, SERVICE_START, "A1", "D1"),
                e(1.0, ARRIVAL, "A2", null),
                e(1.0, SERVICE_START, "A2", "D2"),
                e(2.0, ARRIVAL, "A3", null),
                e(2.0, QUEUED, "A3", null),
                e(3.0, ARRIVAL, "A4", null),
                e(3.0, QUEUED, "A4", null),
                e(4.0, SERVICE_END, "A1", "D1"),
                e(4.0, SERVICE_START, "A3", "D1"),
                e(7.0, ARRIVAL, "A5", null),
                e(7.0, QUEUED, "A5", null),
                e(7.0, SERVICE_END, "A2", "D2"),
                e(7.0, SERVICE_START, "A4", "D2"),
                e(8.0, SERVICE_END, "A3", "D1"),
                e(8.0, SERVICE_START, "A5", "D1"),
                e(12.0, SERVICE_END, "A5", "D1"),
                e(13.0, SERVICE_END, "A4", "D2")), sim.log());
        assertEquals(13.0, sim.now());
        assertEquals(10, sim.processedEventCount()); // 5 arrivals + 5 service completions
    }

    // ---- empty input ----

    @Test
    void emptyAttendeeListProducesEmptyLogAndNoEvents() {
        EventSimulation sim = new EventSimulation(List.of(), List.of(desk("D1", 3.0)));
        sim.run();
        assertTrue(sim.log().isEmpty());
        assertEquals(0.0, sim.now());
        assertEquals(0, sim.processedEventCount());
    }

    // ---- invalid input ----

    @Test
    void rejectsNullCollections() {
        assertThrows(NullPointerException.class, () -> new EventSimulation(null, List.of(desk("D1", 1.0))));
        assertThrows(NullPointerException.class, () -> new EventSimulation(List.of(), null));
    }

    @Test
    void rejectsNullElements() {
        assertThrows(NullPointerException.class, () -> new EventSimulation(
                Arrays.asList(att("A1", 0.0), null), List.of(desk("D1", 1.0))));
        assertThrows(NullPointerException.class, () -> new EventSimulation(
                List.of(att("A1", 0.0)), Arrays.asList(desk("D1", 1.0), null)));
    }

    @Test
    void rejectsEmptyDeskList() {
        assertThrows(IllegalArgumentException.class, () -> new EventSimulation(List.of(att("A1", 0.0)), List.of()));
    }

    @Test
    void rejectsDuplicateAttendeeIds() {
        assertThrows(IllegalArgumentException.class, () -> new EventSimulation(
                List.of(att("A1", 0.0), att("A1", 5.0)), List.of(desk("D1", 1.0))));
    }

    @Test
    void rejectsDuplicateDeskIds() {
        assertThrows(IllegalArgumentException.class, () -> new EventSimulation(
                List.of(att("A1", 0.0)), List.of(desk("D1", 1.0), desk("D1", 2.0))));
    }

    @Test
    void rejectsDeskThatIsAlreadyOccupied() {
        ServiceDesk busy = desk("D1", 1.0);
        busy.startService(att("X", 0.0));
        assertThrows(IllegalArgumentException.class, () -> new EventSimulation(List.of(att("A1", 0.0)), List.of(busy)));
    }

    @Test
    void cannotRunTwice() {
        EventSimulation sim = new EventSimulation(List.of(att("A1", 0.0)), List.of(desk("D1", 1.0)));
        sim.run();
        assertThrows(IllegalStateException.class, sim::run);
    }

    @Test
    void returnedLogCannotBeModified() {
        EventSimulation sim = new EventSimulation(List.of(att("A1", 0.0)), List.of(desk("D1", 1.0)));
        sim.run();
        assertThrows(UnsupportedOperationException.class, () -> sim.log().add(e(0.0, ARRIVAL, "X", null)));
    }
}