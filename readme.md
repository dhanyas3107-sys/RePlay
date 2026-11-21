# RePlay
### Intelligent Scenario Simulation and Decision-Support Platform

RePlay is a Java-based simulation platform designed to help event organisers understand, analyse and improve operational efficiency before an event takes place.

Initially focused on college symposium operations, RePlay models attendee arrivals, service desks and queues to evaluate waiting times, resource utilisation and system performance.

The long-term goal is to introduce stochastic modelling, disruption analysis, scenario comparison and optimisation to support better operational decisions.

## Project Status

**Under active development**

- Phase 0 – Project Setup: Completed
- Phase 1 – Discrete Event Simulation Engine: Completed
- Phase 2 – Event and Operational Modelling: Completed
- Phase 3 – Queueing and Performance Metrics: Completed
- Phase 4 – Stochastic Simulation and Monte Carlo: Planned
- Phase 5 – Disruptions and Dependency Modelling: Planned
- Phase 6 – Scenario Comparison and Analysis: Planned
- Phase 7 – Optimisation and Recommendation Engine: Planned
- Phase 8 – Backend and Frontend Development: Planned
- Phase 9 – AI Integration and Final Validation: Planned

## Features Implemented

- Discrete-event simulation using Java's PriorityQueue.
- Chronological event scheduling with deterministic tie-breaking.
- Attendee arrival and service processing.
- Multiple service desks sharing a FIFO waiting queue.
- Event logging for arrivals, queueing, service starts and completions.
- Average and maximum waiting time calculation.
- Average time in system.
- Maximum queue length tracking.
- Service desk and overall utilisation metrics.
- System throughput calculation.
- Unit testing with JUnit 5.

## Technology Stack

- Java 24
- Maven
- JUnit 5
- Git and GitHub

## Project Structure

```text
RePlay/
├── docs/
│   ├── assumptions.md
│   ├── decisions.md
│   ├── metrics.md
│   └── validation.md
├── replay-core/
│   └── src/
│       ├── main/java/com/replay/core/
│       └── test/java/com/replay/core/
├── replay-cli/
│   └── src/main/java/com/replay/cli/
├── pom.xml
├── readme.md
└── .gitignore
```

## Getting Started

### Prerequisites

- Java JDK 24
- Maven 3.9 or later

### Build the project

```bash
mvn clean package
```

### Run the tests

```bash
mvn clean test
```

### Run the metrics demonstration

```bash
java -cp replay-cli/target/replay-cli-0.1.0-SNAPSHOT.jar:replay-core/target/replay-core-0.1.0-SNAPSHOT.jar com.replay.cli.MetricsDemo
```

## Design Principles

1. Simulation results must be derived from the simulation engine.
2. Simulation runs should be reproducible through controlled random seeds.
3. Performance metrics are calculated separately from simulation operations.
4. The simulation engine remains independent of AI-generated recommendations.
5. Each development phase is validated before introducing additional complexity.

## Future Direction

RePlay aims to evolve into a decision-support platform capable of modelling uncertain event conditions, evaluating operational disruptions, comparing alternative strategies and identifying resource allocation improvements.

AI-assisted explanations and recommendations will be introduced only after the underlying simulation and analytical components are established.

---

**RePlay is an ongoing project. Features listed as planned are not yet implemented.**