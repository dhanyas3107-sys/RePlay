# Assumptions

Arrival rates, service times and similar parameters are assumptions unless
calibrated against real data. Each assumption is listed here with its source.

| # | Assumption | Source | Status |
|---|------------|--------|--------|
| A1 | Arrival times are fixed, supplied inputs (Phase 2 is deterministic) | Modelling choice | Replaced by random arrivals in a later phase |
| A2 | Each desk has one fixed service duration > 0 for every attendee | Modelling choice | Replaced by distributions later |
| A3 | One shared FIFO queue feeds all desks; no balking, reneging or priorities | Modelling choice | Open to revisit |
| A4 | Attendees move instantly from arrival to queue/desk (no walking time) | Modelling choice | Travel delays planned for Phase 4 |
| A5 | Desks never fail or take breaks | Modelling choice | Disruptions planned for Phase 5 |