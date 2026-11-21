# Design Decisions

## D1: Multi-module Maven (replay-core, replay-cli)
The build prevents the core engine from depending on any interface code.

## D2: Custom discrete-event engine on PriorityQueue
Chosen for learning value. Correctness is established by validation against
queueing theory (see validation.md).

## D3: Jackson for JSON (added in Phase 1)
The only planned runtime dependency.

## D4: Java 24 with features available since Java 21
Switching to Java 21 or 25 requires changing one property in the parent POM.
## D5: Event-scheduling engine semantics (Phase 1)
- Time is a double in minutes; times must be finite and >= 0.
- Events are ordered by (time, sequence). Sequence is a per-engine counter,
  so events at the same time run FIFO. This makes runs deterministic.
- Scheduling in the past is rejected, so the clock never goes backwards.
- runUntil(t) is inclusive of events at exactly t, then sets the clock to t.
  Later events stay pending. run() leaves the clock at the last event time.
- If an event throws, the exception propagates. The failed event is removed
  and not counted; other events stay pending; the engine remains usable.
- Re-entrant run()/runUntil() calls throw IllegalStateException.

## D6: Events receive SimulationContext, not the engine
Events can read the time and schedule events, but cannot control the run loop.

## D10: Metrics are computed from the event log only (Phase 3)
`MetricsCalculator` reads the log and never touches the engine or the operational
model. The core method is a pure function of (log, desk IDs) and validates the log strictly.

## D11: Observation window
First arrival to final completion. Total simulation duration, throughput and
utilisation all use this window, so the three figures are consistent.

## D12: Maximum queue length is measured after each instant
Queue length is read after all events at a timestamp. Zero-duration visits caused
by the arrivals-before-completions tie rule (D7) are not counted.

## D13: Undefined averages are 0.0
No NaN or infinity ever appears in a result. Counts disambiguate "no data".

## D14: Two additive accessors on EventSimulation
`deskIds()` (so idle desks are reported) and `hasRun()` (so an un-run simulation is
not mistaken for an empty one). No existing behaviour changed.