# RePlay

Intelligent Scenario Simulation and Decision-Support Platform.
Initial focus: simulating and analysing operations at a college symposium.

## Status
Phase 0: project skeleton. No simulation functionality yet.

## Modules
- `replay-core`: simulation engine, models, analysis, scenarios (no UI, no AI)
- `replay-cli`: command-line interface, depends on `replay-core`

## Requirements
Java 24, Maven 3.9+

## Build and test
    mvn clean test

## Principles
1. Every reported number comes from the simulation engine.
2. Runs are seeded and reproducible.
3. The engine is validated against queueing theory before features are added.
4. AI is kept separate from the engine and never produces results itself.