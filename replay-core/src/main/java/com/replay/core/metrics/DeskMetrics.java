package com.replay.core.metrics;

import java.util.Objects;

/**
 * Performance of one service desk over the observation window.
 *
 * @param deskId             the desk
 * @param attendeesServed    number of services this desk completed
 * @param totalBusyTime      sum of (service end - service start) over its completed services, in minutes
 * @param utilisationPercent totalBusyTime / observation window * 100; 0.0 if the window has zero length
 */
public record DeskMetrics(String deskId, int attendeesServed, double totalBusyTime, double utilisationPercent) {

    public DeskMetrics {
        Objects.requireNonNull(deskId, "deskId must not be null");
        if (attendeesServed < 0 || totalBusyTime < 0 || utilisationPercent < 0) {
            throw new IllegalArgumentException("DeskMetrics values must not be negative");
        }
    }
}