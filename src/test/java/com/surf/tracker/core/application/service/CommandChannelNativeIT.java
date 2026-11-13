package com.surf.tracker.core.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.shared.commands.WriteOutcome;
import com.surf.tracker.shared.commands.CommandWrite;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import com.surf.tracker.shared.redis.RedisJsonSerializer;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

/** Explicitly selected by the owned native runner, never contacts deployment infrastructure. */
class CommandChannelNativeIT {
    @Test void backendDispatcher_separateGatewayProcess_writesExactBytesOnce() throws Exception {
        String origin = System.getenv("COMMAND_GATEWAY_ORIGIN");
        assertThat(origin).startsWith("http://127.0.0.1:");
        var factory = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getenv("REDIS_PORT")));
        factory.afterPropertiesSet(); factory.start();
        try {
            var mapper = new ObjectMapper().findAndRegisterModules().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(factory); template.setKeySerializer(new StringRedisSerializer());
            template.setValueSerializer(RedisJsonSerializer.create(mapper)); template.afterPropertiesSet();
            var connections = new RedisConnectionTracker(template, Clock.systemUTC());
            var channel = new HttpCommandWriteChannel(mapper, true, origin, System.getenv("COMMAND_CHANNEL_SECRET"), 5);
            var trackers = mock(TrackerRepository.class);
            Tracker tracker = new Tracker(); tracker.setTrackerId("owned"); tracker.setDeviceId("8168000008");
            when(trackers.findByIdAndOrganizationId("owned", 1L)).thenReturn(Optional.of(tracker));
            TenantContext.setCurrentOrgId(1L); TenantContext.setCurrentUserId(7L);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", "unused", List.of(new SimpleGrantedAuthority("devices.command"))));
            var dispatcher = new TrackerCommandDispatcher(trackers, connections, channel, Clock.systemUTC());
            try (var peer = new Socket("127.0.0.1", Integer.parseInt(System.getenv("COMMAND_NATIVE_TCP_PORT")))) {
                peer.setSoTimeout(5000);
                String frame = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
                peer.getOutputStream().write(frame.getBytes(StandardCharsets.US_ASCII));
                await().atMost(Duration.ofSeconds(15)).until(() -> connections.getConnectionIdByDeviceId("8168000008") != null);
                UUID attempt = UUID.randomUUID(); Instant deadline = Instant.now().plusSeconds(20);
                byte[] bytes = {0x78, 0, (byte) 255, 13, 10};
                assertThat(dispatcher.dispatch("owned", attempt, deadline, bytes).outcome()).isEqualTo(WriteOutcome.SENT);
                assertThat(peer.getInputStream().readNBytes(bytes.length)).containsExactly(bytes);
                assertThat(dispatcher.dispatch("owned", attempt, deadline, bytes).outcome()).isEqualTo(WriteOutcome.SENT);
                peer.setSoTimeout(250);
                assertThatThrownBy(() -> peer.getInputStream().read()).isInstanceOf(java.net.SocketTimeoutException.class);
                assertThat(dispatcher.dispatch("owned", attempt, deadline, new byte[]{2}).outcome()).isEqualTo(WriteOutcome.ID_CONFLICT);
                assertThat(dispatcher.dispatch("owned", UUID.randomUUID(), Instant.now().minusSeconds(1), bytes).outcome()).isEqualTo(WriteOutcome.EXPIRED);
                String oldConnection = connections.getConnectionIdByDeviceId("8168000008");
                try (var replacement = new Socket("127.0.0.1", Integer.parseInt(System.getenv("COMMAND_NATIVE_TCP_PORT")))) {
                    replacement.setSoTimeout(5000);
                    replacement.getOutputStream().write(frame.getBytes(StandardCharsets.US_ASCII));
                    await().atMost(Duration.ofSeconds(10)).until(() -> {
                        String current = connections.getConnectionIdByDeviceId("8168000008");
                        return current != null && !current.equals(oldConnection);
                    });
                    String currentConnection = connections.getConnectionIdByDeviceId("8168000008");
                    assertThat(channel.write(new CommandWrite(UUID.randomUUID(), "8168000008", oldConnection, Instant.now().plusSeconds(15), bytes)).outcome()).isEqualTo(WriteOutcome.STALE_CONNECTION);
                    peer.close();
                    await().atMost(Duration.ofSeconds(10)).until(() -> !Boolean.TRUE.equals(template.hasKey("tracker:connection:" + oldConnection)));
                    assertThat(connections.getConnectionIdByDeviceId("8168000008")).isEqualTo(currentConnection);
                    assertThat(dispatcher.dispatch("owned", UUID.randomUUID(), Instant.now().plusSeconds(15), bytes).outcome()).isEqualTo(WriteOutcome.SENT);
                    assertThat(replacement.getInputStream().readNBytes(bytes.length)).containsExactly(bytes);
                }
            }
            verify(trackers, atLeastOnce()).findByIdAndOrganizationId("owned", 1L);
        } finally { TenantContext.clear(); SecurityContextHolder.clearContext(); factory.destroy(); }
    }
}
