package com.surf.tracker.devicecomm.service;

import com.surf.tracker.devicecomm.application.DeviceDataProcessor;
import com.surf.tracker.devicecomm.application.ParserFactory;
import com.surf.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.netty.DisposableServer;

import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class AutoseekerSocketIntegrationTest {
    private static final String VALID = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";

    @Test
    void receive_fragmentedAndCoalescedFrames_publishesOnlyValidGpsFixes() throws Exception {
        // given: actual socket/framer/parser/processor, mocked external Redis/JMS adapters.
        var publications = new LinkedBlockingQueue<LocationDataEvent>();
        EventPublisher publisher = mock(EventPublisher.class);
        RedisConnectionTracker connections = mock(RedisConnectionTracker.class);
        doAnswer(invocation -> {
            publications.add(invocation.getArgument(0));
            return null;
        }).when(publisher).publish(any(LocationDataEvent.class));
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(EventPublisher.class, () -> publisher);
            context.registerBean(RedisConnectionTracker.class, () -> connections);
            context.registerBean(ParserFactory.class, () -> new ParserFactory(List.of(new AutoseekerProtocolParser())));
            context.registerBean(Clock.class, () -> Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC));
            context.registerBean(DeviceDataProcessor.class);
            context.registerBean(ReactiveTcpServer.class);
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                "socket-test", java.util.Map.of("tracking.tcp.server.port", "0",
                    "tracking.tcp.server.host", "127.0.0.1")));
            context.refresh();
            var gateway = context.getBean(ReactiveTcpServer.class);
            gateway.start();
            try {
                var bound = (DisposableServer) ReflectionTestUtils.getField(gateway, "server");
                assertThat(((java.net.InetSocketAddress) bound.address()).getAddress().isLoopbackAddress()).isTrue();
                int port = bound.port();
                try (var socket = new Socket("127.0.0.1", port)) {
                    // when: split one packet, then invalid + valid packets in a single write.
                    var output = socket.getOutputStream();
                    output.write(VALID.substring(0, 24).getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    output.write(VALID.substring(24).getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    assertValid(publications.poll(5, TimeUnit.SECONDS));
                    String malformed = VALID.replace("2234.9273", "NaN").replace("8168000008", "8168000009");
                    output.write((malformed + VALID).getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    // then: invalid telemetry never becomes a published synthetic zero location.
                    assertValid(publications.poll(5, TimeUnit.SECONDS));
                    assertThat(publications.poll(500, TimeUnit.MILLISECONDS)).isNull();
                    verify(connections, never()).updateConnectionDevice(anyString(), eq("8168000009"));
                }
                try (var socket = new Socket("127.0.0.1", port)) {
                    // A non-ASCII byte in otherwise unused status metadata must not be repaired into '?'.
                    byte[] invalidBytes = VALID.getBytes(StandardCharsets.US_ASCII);
                    invalidBytes[VALID.indexOf("FBFBBFF")] = (byte) 0xff;
                    socket.getOutputStream().write(invalidBytes);
                    socket.getOutputStream().flush();
                    socket.setSoTimeout(5000);
                    assertThat(socket.getInputStream().read()).isEqualTo(-1);
                    assertThat(publications.poll(500, TimeUnit.MILLISECONDS)).isNull();
                }
                try (var socket = new Socket("127.0.0.1", port)) {
                    socket.getOutputStream().write(VALID.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    assertValid(publications.poll(5, TimeUnit.SECONDS));
                }
            } finally {
                gateway.stop();
            }
        }
    }

    private void assertValid(LocationDataEvent event) {
        assertThat(event).isNotNull();
        assertThat(event.getLocation().timestamp()).isEqualTo(Instant.parse("2015-07-10T04:36:02Z"));
        assertThat(event.getLocation().isValid()).isTrue();
    }
}
