package com.surf.tracker.shared.commands;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CommandWriteTest {
    @Test void create_binaryPayload_isBoundedAndDefensivelyCopied() {
        byte[] bytes = {0, 1, (byte) 255};
        var request = new CommandWrite(UUID.randomUUID(), "gps", "connection", Instant.parse("2026-11-13T12:00:00Z"), bytes);
        bytes[0] = 9;
        assertThat(request.payload()).containsExactly(0, 1, (byte) 255);
        request.payload()[0] = 8;
        assertThat(request.payload()[0]).isZero();
        assertThatThrownBy(() -> new CommandWrite(UUID.randomUUID(), "gps", "connection", Instant.now(), new byte[4097]))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
