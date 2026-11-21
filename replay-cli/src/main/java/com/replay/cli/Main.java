package com.replay.cli;

import com.replay.core.ReplayInfo;

/** Phase 0 entry point. Proves that the CLI module can see the core module. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println(ReplayInfo.name() + " CLI: build OK (no simulation yet)");
    }
}