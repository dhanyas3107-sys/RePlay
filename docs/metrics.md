# Metrics (Phase 3)

Metrics are computed by `MetricsCalculator` from the event log of a finished
`EventSimulation`. The engine and the operational model contain no analytics code.

## Observation window
`[observationStart, observationEnd]`
- observationStart = time of the first ARRIVAL in the log
- observationEnd   = time of the last SERVICE_END in the log
- window length    = observationEnd - observationStart = "total simulation duration"

Throughput and utilisation both use this window. Time before the first arrival
(e.g. if the first attendee arrives at t=10) is not counted. Edge cases: no
arrivals gives [0, 0]; arrivals but no completion gives [start, start]. A window
of length 0 makes every window-based rate 0.0 (no division by zero).

## Definitions
| Metric | Definition |
|---|---|
| Total attendees | Number of ARRIVAL entries |
| Completed attendees | Attendees with a SERVICE_END entry |
| Waiting time | SERVICE_START time - ARRIVAL time (completed attendees) |
| Average / maximum waiting time | Mean / max of waiting time |
| Time in system | SERVICE_END time - ARRIVAL time (completed attendees) |
| Average time in system | Mean of time in system |
| Maximum queue length | See "Queue length" below |
| Throughput | completed attendees / window length (attendees per minute) |
| Desk busy time | Sum of (SERVICE_END - SERVICE_START) over the desk's completed services |
| Desk attendees served | Number of SERVICE_END entries at that desk |
| Desk utilisation % | desk busy time / window length * 100 |
| Overall utilisation % | total busy time of all desks / (number of desks * window length) * 100 |

Idle desks are included (they report 0%) and lower the overall figure.

## How the log is used
Each attendee's arrival, service start and service end times are collected from
ARRIVAL, SERVICE_START and SERVICE_END entries. Per desk, a SERVICE_START opens a
busy interval and the matching SERVICE_END closes it. The log is validated while
scanning: an inconsistent log (service ends without starting, a desk starting
while busy, a desk mismatch, time going backwards, duplicate arrivals, unknown
desks) throws IllegalArgumentException instead of producing wrong numbers.

## Queue length
A QUEUED entry adds the attendee to the queue; their SERVICE_START removes them.
The queue length is read only after all entries with the same timestamp have been
applied, so a state that exists for zero time is not counted.

Why this matters: because arrivals are processed before completions at the same
instant (D7), an attendee who arrives exactly when a desk frees up is logged
QUEUED and then SERVICE_START at the same time. They wait 0 minutes.

Example (one desk, 5 min per attendee; A1 arrives at 0, A2 at 1, A3 at 5).
All at t=5 the log reads: ARRIVAL A3, QUEUED A3, SERVICE_END A1, SERVICE_START A2.
Counting after each entry would see {A2, A3} = 2. The real queue is {A2} during
[1, 5) and {A3} during [5, 10), so the maximum queue length is 1.

## Conventions
- Values are never rounded in the model; only the demo rounds for display.
- Averages are 0.0 when no attendee completed. Check `completedAttendees` to
  tell "no data" from "really zero".

## Limitations
- One deterministic run. There are no confidence intervals and no statistical
  claims; those come with random inputs and replications.
- Waiting and system-time statistics cover completed attendees only, and
  unfinished services are excluded from busy time. A finished EventSimulation
  has none of these cases; only partial hand-built logs do.
- Window-based figures depend on the window definition. Utilisation measured over
  a different window (e.g. the whole event schedule) would differ.
- Queue length is measured at event boundaries; time-averaged queue length is not
  computed yet.
- There is no per-desk queue, since the model has one shared queue.
- No theoretical queueing formulas are used. Validation against them comes later.