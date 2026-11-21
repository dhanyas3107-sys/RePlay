package com.replay.core.engine;

/**
 * The work an event performs when its time arrives.
 *
 * <p>This is a functional interface, so an action can be written as a lambda:
 * {@code ctx -> System.out.println("t=" + ctx.now())}.
 *
 * <p>The {@link SimulationContext} is the only thing an action receives. It
 * lets the action read the clock and schedule follow-up events, but not run or
 * control the engine itself.
 */
@FunctionalInterface
public interface EventAction {

    void execute(SimulationContext context);
}