package com.surf.tracker.shared.commands;

import java.util.UUID;

public record CommandWriteResult(UUID attemptId, WriteOutcome outcome) {
    public CommandWriteResult {
        if (attemptId == null || outcome == null) throw new IllegalArgumentException("Invalid command write result");
    }
}
