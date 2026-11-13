package com.surf.tracker.shared.commands;

/** SENT means a completed local socket write, never hardware delivery/execution/ACK. */
public enum WriteOutcome {
    SENT, NOT_CONNECTED, STALE_CONNECTION, EXPIRED, REJECTED, ID_CONFLICT, UNKNOWN, DISABLED
}
