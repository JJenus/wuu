package com.surf.tracker.devicecomm.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.devicecomm.pipeline.IngestPipeline;
import com.surf.tracker.devicecomm.pipeline.ProtocolRegistry;
import com.surf.tracker.devicecomm.protocol.autoseeker.AutoseekerProtocol;
import com.surf.tracker.devicecomm.service.ReactiveTcpServer;
import com.surf.tracker.shared.commands.*;
import com.surf.tracker.shared.domain.ConnectionInfo;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import java.net.Socket;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandGatewaySocketIntegrationTest {
    private static final String VALID = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
    private static final String SECRET = "test-only-command-secret-at-least-32-chars";
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void channel_realHttpAndTcp_writesExactBytesAndPinsDeviceIdentity() throws Exception {
        var publisher = mock(EventPublisher.class);
        var connections = mock(RedisConnectionTracker.class);
        var live = new ConcurrentHashMap<String, Connection>();
        var identified = new LinkedBlockingQueue<String>();
        var events = new LinkedBlockingQueue<LocationDataEvent>();
        var route = new AtomicReference<ConnectionInfo>();
        doAnswer(call -> { live.put(call.getArgument(0, String.class), call.getArgument(3, Connection.class)); return null; })
            .when(connections).registerConnection(anyString(), anyString(), anyString(), any());
        when(connections.getLiveConnection(anyString())).thenAnswer(call -> live.get(call.getArgument(0, String.class)));
        doAnswer(call -> {
            String connection = call.getArgument(0, String.class), device = call.getArgument(1, String.class);
            route.set(new ConnectionInfo(connection, device, "127.0.0.1", Instant.now(), Instant.now()));
            identified.add(connection); return null;
        }).when(connections).updateConnectionDevice(anyString(), anyString());
        when(connections.getConnectionByDeviceId(anyString())).thenAnswer(call -> {
            var current = route.get(); return current != null && current.getDeviceId().equals(call.getArgument(0, String.class)) ? current : null;
        });
        doAnswer(call -> { events.add(call.getArgument(0, LocationDataEvent.class)); return null; }).when(publisher).publish(any());
        try (var context = new AnnotationConfigApplicationContext()) {
            org.springframework.boot.test.util.TestPropertyValues.of("tracking.tcp.server.port=0", "tracking.tcp.server.host=127.0.0.1").applyTo(context);
            context.registerBean(EventPublisher.class, () -> publisher);
            context.registerBean(RedisConnectionTracker.class, () -> connections);
            context.registerBean(ProtocolRegistry.class, () -> new ProtocolRegistry(List.of(new AutoseekerProtocol())));
            context.registerBean(Clock.class, Clock::systemUTC);
            context.registerBean(IngestPipeline.class); context.registerBean(ReactiveTcpServer.class); context.refresh();
            var tcp = context.getBean(ReactiveTcpServer.class); tcp.start();
            try (var gateway = new CommandGatewayServer(tcp, mapper, Clock.systemUTC(), "127.0.0.1", 0, SECRET, directory.toString(), 20)) {
                gateway.start();
                int port = ((DisposableServer) ReflectionTestUtils.getField(tcp, "server")).port();
                try (var peer = new Socket("127.0.0.1", port)) {
                    peer.setSoTimeout(5000); peer.getOutputStream().write(VALID.getBytes(StandardCharsets.US_ASCII));
                    String connection = identified.poll(5, TimeUnit.SECONDS);
                    assertThat(connection).isNotNull(); assertThat(events.poll(5, TimeUnit.SECONDS)).isNotNull();
                    byte[] payload = {0x78, 0, (byte) 255, 13, 10};
                    var request = new CommandWrite(UUID.randomUUID(), "8168000008", connection, Instant.now().plusSeconds(20), payload);
                    assertThat(write(gateway.port(), request)).isEqualTo(WriteOutcome.SENT);
                    assertThat(peer.getInputStream().readNBytes(payload.length)).containsExactly(payload);
                    assertThat(write(gateway.port(), request)).isEqualTo(WriteOutcome.SENT);
                    peer.setSoTimeout(200);
                    assertThatThrownBy(() -> peer.getInputStream().read()).isInstanceOf(java.net.SocketTimeoutException.class);
                    assertThat(write(gateway.port(), new CommandWrite(request.attemptId(), request.deviceId(), connection, request.expiresAt(), new byte[]{2}))).isEqualTo(WriteOutcome.ID_CONFLICT);
                    assertThat(write(gateway.port(), new CommandWrite(UUID.randomUUID(), "8168000009", connection, request.expiresAt(), payload))).isEqualTo(WriteOutcome.STALE_CONNECTION);
                    assertThat(write(gateway.port(), new CommandWrite(UUID.randomUUID(), request.deviceId(), "old-connection", request.expiresAt(), payload))).isEqualTo(WriteOutcome.NOT_CONNECTED);
                    // A later frame cannot steal this already identified connection.
                    peer.getOutputStream().write((VALID.replace("8168000008", "8168000009") + VALID).getBytes(StandardCharsets.US_ASCII));
                    assertThat(events.poll(5, TimeUnit.SECONDS).getDeviceId()).isEqualTo("8168000008");
                    assertThat(identified.poll(5, TimeUnit.SECONDS)).isEqualTo(connection);
                    verify(connections, never()).updateConnectionDevice(anyString(), eq("8168000009"));
                }
            } finally { tcp.stop(); }
        }
    }
    private WriteOutcome write(int port, CommandWrite command) throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/command-writes"))
            .header("X-Command-Secret", SECRET).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(command))).build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readValue(response.body(), CommandWriteResult.class).outcome();
    }
}
