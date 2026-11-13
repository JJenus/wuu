package com.surf.tracker.core.application.service;

import com.surf.tracker.core.domain.entity.Tracker;
import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.shared.commands.*;
import com.surf.tracker.shared.domain.ConnectionInfo;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import com.surf.tracker.shared.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrackerCommandDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private final TrackerRepository trackers = mock(TrackerRepository.class);
    private final RedisConnectionTracker connections = mock(RedisConnectionTracker.class);
    private final CommandWriteChannel channel = mock(CommandWriteChannel.class);
    @AfterEach void clear() { TenantContext.clear(); SecurityContextHolder.clearContext(); }
    private void authorize() {
        TenantContext.setCurrentOrgId(1L); TenantContext.setCurrentUserId(7L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("7", "unused", List.of(new SimpleGrantedAuthority("devices.command"))));
    }
    @Test void dispatch_authorizedTracker_bindsExactConnectionAndReturnsOnlySocketOutcome() {
        authorize();
        Tracker tracker = new Tracker(); tracker.setTrackerId("tracker"); tracker.setDeviceId("gps");
        when(trackers.findByIdAndOrganizationId("tracker", 1L)).thenReturn(Optional.of(tracker));
        when(connections.getConnectionByDeviceId("gps")).thenReturn(new ConnectionInfo("connection", "gps", "127.0.0.1", NOW, NOW));
        UUID id = UUID.randomUUID();
        when(channel.write(any())).thenReturn(new CommandWriteResult(id, WriteOutcome.SENT));
        var dispatcher = new TrackerCommandDispatcher(trackers, connections, channel, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(dispatcher.dispatch("tracker", id, NOW.plusSeconds(15), new byte[]{0, 1}).outcome()).isEqualTo(WriteOutcome.SENT);
        verify(channel).write(argThat(request -> request.attemptId().equals(id) && request.deviceId().equals("gps") && request.connectionId().equals("connection")));
    }
    @Test void dispatch_missingPermissionOrTenant_neverReachesDeviceRoute() {
        var dispatcher = new TrackerCommandDispatcher(trackers, connections, channel, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> dispatcher.dispatch("tracker", UUID.randomUUID(), NOW.plusSeconds(15), new byte[]{1}))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        authorize(); TenantContext.setCurrentOrgId(null);
        assertThatThrownBy(() -> dispatcher.dispatch("tracker", UUID.randomUUID(), NOW.plusSeconds(15), new byte[]{1}))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        verifyNoInteractions(trackers, connections, channel);
    }
    @Test void dispatch_foreignOrMissingTracker_returns404WithoutRouteLookup() {
        authorize(); when(trackers.findByIdAndOrganizationId("foreign", 1L)).thenReturn(Optional.empty());
        var dispatcher = new TrackerCommandDispatcher(trackers, connections, channel, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> dispatcher.dispatch("foreign", UUID.randomUUID(), NOW.plusSeconds(15), new byte[]{1}))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verifyNoInteractions(connections, channel);
    }
    @Test void dispatch_offlineTracker_doesNotContactGateway() {
        authorize(); Tracker tracker = new Tracker(); tracker.setDeviceId("gps");
        when(trackers.findByIdAndOrganizationId("tracker", 1L)).thenReturn(Optional.of(tracker));
        var dispatcher = new TrackerCommandDispatcher(trackers, connections, channel, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(dispatcher.dispatch("tracker", UUID.randomUUID(), NOW.plusSeconds(15), new byte[]{1}).outcome()).isEqualTo(WriteOutcome.NOT_CONNECTED);
        verifyNoInteractions(channel);
    }
}
