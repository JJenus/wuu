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

    @Test void write_realHttp_checksCredentialsCorrelationAndResponseBoundsWithoutRedirects() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var mode = new java.util.concurrent.atomic.AtomicInteger();
        var received = new java.util.concurrent.atomic.AtomicReference<CommandWrite>();
        String secret = "test-only-command-secret-at-least-32-chars";
        server.createContext("/internal/command-writes", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Command-Secret")).isEqualTo(secret);
            CommandWrite request = mapper.readValue(exchange.getRequestBody().readAllBytes(), CommandWrite.class);
            received.set(request);
            int status = mode.get() == 3 ? 302 : mode.get() == 4 ? 401 : 200;
            if (status == 302) exchange.getResponseHeaders().add("Location", "/must-not-follow");
            byte[] body = mode.get() == 2 ? new byte[2048] : mapper.writeValueAsBytes(
                new CommandWriteResult(mode.get() == 1 ? UUID.randomUUID() : request.attemptId(), WriteOutcome.SENT));
            exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var globalSnakeCase = mapper.copy().setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);
            var channel = new HttpCommandWriteChannel(globalSnakeCase, true, "http://127.0.0.1:" + server.getAddress().getPort(), secret, 2);
            var request = new CommandWrite(UUID.randomUUID(), "gps", "connection", Instant.now().plusSeconds(15), new byte[]{0, 1, (byte) 255});
            assertThat(channel.write(request).outcome()).isEqualTo(WriteOutcome.SENT);
            assertThat(received.get().payload()).containsExactly(request.payload());
            for (int variant : new int[]{1, 2, 3}) {
                mode.set(variant); assertThat(channel.write(request).outcome()).isEqualTo(WriteOutcome.UNKNOWN);
            }
            mode.set(4); assertThat(channel.write(request).outcome()).isEqualTo(WriteOutcome.REJECTED);
        } finally { server.stop(0); }
    }
}
