package com.surf.tracker.devicecomm.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.devicecomm.service.ReactiveTcpServer;
import com.surf.tracker.shared.commands.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandGatewayServerTest {
    @TempDir Path directory;
    private static final String SECRET = "test-only-command-secret-at-least-32-chars";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Test void write_authenticatedBoundedRequest_returnsSocketResultAndNeverRepeatsIt() throws Exception {
        var socket = mock(ReactiveTcpServer.class);
        when(socket.writeCommand(any())).thenReturn(WriteOutcome.SENT);
        try (var gateway = new CommandGatewayServer(socket, mapper, Clock.systemUTC(), "127.0.0.1", 0, SECRET, directory.toString(), 10)) {
            gateway.start();
            var request = new CommandWrite(UUID.randomUUID(), "gps", "connection", Instant.now().plusSeconds(15), new byte[]{0, 1, (byte) 255});
            byte[] body = mapper.writeValueAsBytes(request);
            assertThat(post(gateway.port(), body, "wrong").statusCode()).isEqualTo(401);
            verifyNoInteractions(socket);
            var response = post(gateway.port(), body, SECRET);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(mapper.readValue(response.body(), CommandWriteResult.class).outcome()).isEqualTo(WriteOutcome.SENT);
            assertThat(post(gateway.port(), body, SECRET).statusCode()).isEqualTo(200);
            verify(socket, times(1)).writeCommand(any());
            assertThat(post(gateway.port(), new byte[9000], SECRET).statusCode()).isEqualTo(413);
            var expired = new CommandWrite(UUID.randomUUID(), "gps", "connection", Instant.now().minusSeconds(1), new byte[]{1});
            assertThat(mapper.readValue(post(gateway.port(), mapper.writeValueAsBytes(expired), SECRET).body(), CommandWriteResult.class).outcome()).isEqualTo(WriteOutcome.EXPIRED);
            verifyNoMoreInteractions(socket);
        }
    }
    private HttpResponse<byte[]> post(int port, byte[] bytes, String secret) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/command-writes"))
            .header("X-Command-Secret", secret).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
