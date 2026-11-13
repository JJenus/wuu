package com.surf.tracker.shared.commands;

public interface CommandWriteChannel {
    /** Exactly one network attempt. Callers must persist and reuse the attempt identity. */
    CommandWriteResult write(CommandWrite command);
}
