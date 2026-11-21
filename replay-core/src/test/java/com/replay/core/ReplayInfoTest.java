package com.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReplayInfoTest {

    @Test
    void nameIsReplay() {
        assertEquals("RePlay", ReplayInfo.name());
    }
}