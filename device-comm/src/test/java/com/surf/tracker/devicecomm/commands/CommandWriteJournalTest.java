package com.surf.tracker.devicecomm.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.shared.commands.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class CommandWriteJournalTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private CommandWrite request(UUID id, byte[] payload) {
        return new CommandWrite(id, "gps", "connection", Instant.parse("2026-11-13T12:00:00Z"), payload);
    }
    @Test void write_repeatedRequestAndRestart_neverRepeatsSocketAttempt() throws Exception {
        var counter = new AtomicInteger();
        var request = request(UUID.randomUUID(), new byte[]{1, 2});
        try (var journal = new CommandWriteJournal(directory, mapper, 10)) {
            assertThat(journal.execute(request, () -> { counter.incrementAndGet(); return WriteOutcome.SENT; })).isEqualTo(WriteOutcome.SENT);
            assertThat(journal.execute(request, () -> { counter.incrementAndGet(); return WriteOutcome.SENT; })).isEqualTo(WriteOutcome.SENT);
            assertThat(journal.execute(request(request.attemptId(), new byte[]{3}), () -> WriteOutcome.SENT)).isEqualTo(WriteOutcome.ID_CONFLICT);
        }
        try (var journal = new CommandWriteJournal(directory, mapper, 10)) {
            assertThat(journal.execute(request, () -> { counter.incrementAndGet(); return WriteOutcome.SENT; })).isEqualTo(WriteOutcome.SENT);
        }
        assertThat(counter.get()).isEqualTo(1);
    }
    @Test void write_crashWindowIsUnknownAndFullJournalFailsClosed() throws Exception {
        var request = request(UUID.randomUUID(), new byte[]{1});
        try (var journal = new CommandWriteJournal(directory, mapper, 1)) {
            assertThatThrownBy(() -> journal.execute(request, () -> { throw new IllegalStateException("synthetic interruption"); })).isInstanceOf(IllegalStateException.class);
            assertThat(journal.execute(request, () -> WriteOutcome.SENT)).isEqualTo(WriteOutcome.UNKNOWN);
            assertThat(journal.execute(request(UUID.randomUUID(), new byte[]{2}), () -> WriteOutcome.SENT)).isEqualTo(WriteOutcome.REJECTED);
        }
    }
}
