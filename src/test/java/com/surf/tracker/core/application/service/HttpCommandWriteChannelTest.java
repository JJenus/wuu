package com.surf.tracker.core.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.shared.commands.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class HttpCommandWriteChannelTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Test void configure_remotePlaintextOrMissingSecret_refusesUnsafeChannel() {
        assertThatThrownBy(() -> new HttpCommandWriteChannel(mapper, true, "http://example.com", "x".repeat(32), 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HttpCommandWriteChannel(mapper, true, "http://127.0.0.1:1", "", 10)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void write_disabledOrUnavailableChannel_neverClaimsDeviceDelivery() {
        var request = new CommandWrite(UUID.randomUUID(), "gps", "connection", Instant.now().plusSeconds(15), new byte[]{1});
        assertThat(new HttpCommandWriteChannel(mapper, false, "", "", 10).write(request).outcome()).isEqualTo(WriteOutcome.DISABLED);
        assertThat(new HttpCommandWriteChannel(mapper, true, "http://127.0.0.1:1", "x".repeat(32), 1).write(request).outcome()).isEqualTo(WriteOutcome.UNKNOWN);
    }
}
