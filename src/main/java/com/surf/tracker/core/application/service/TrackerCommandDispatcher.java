package com.surf.tracker.core.application.service;

import com.surf.tracker.core.infrastructure.repository.TrackerRepository;
import com.surf.tracker.shared.commands.CommandWrite;
import com.surf.tracker.shared.commands.CommandWriteChannel;
import com.surf.tracker.shared.commands.CommandWriteResult;
import com.surf.tracker.shared.commands.WriteOutcome;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import com.surf.tracker.shared.security.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Internal use-case seam for trusted protocol encoders; deliberately has no raw-command HTTP route. */
@Service
public class TrackerCommandDispatcher {
    private final TrackerRepository trackers;
    private final RedisConnectionTracker connections;
    private final CommandWriteChannel channel;
    private final Clock clock;
    public TrackerCommandDispatcher(TrackerRepository trackers, RedisConnectionTracker connections, CommandWriteChannel channel, Clock clock) {
        this.trackers = trackers; this.connections = connections; this.channel = channel; this.clock = clock;
    }

    /** Caller must persist/reuse attemptId, expiry and encoded bytes for one logical attempt. */
    public CommandWriteResult dispatch(String trackerId, UUID attemptId, Instant expiresAt, byte[] encodedPayload) {
        Long organizationId = TenantContext.getCurrentOrgId();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (organizationId == null || organizationId <= 0 || TenantContext.getCurrentUserId() == null
            || authentication == null || !authentication.isAuthenticated()
            || authentication.getAuthorities().stream().noneMatch(authority -> "devices.command".equals(authority.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active tenant and devices.command permission required");
        if (attemptId == null || expiresAt == null || expiresAt.isAfter(clock.instant().plusSeconds(30))
            || encodedPayload == null || encodedPayload.length == 0 || encodedPayload.length > 4096)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid encoded command attempt");
        var tracker = trackers.findByIdAndOrganizationId(trackerId, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tracker not found"));
        if (!expiresAt.isAfter(clock.instant())) return new CommandWriteResult(attemptId, WriteOutcome.EXPIRED);
        if (tracker.getDeviceId() == null) return new CommandWriteResult(attemptId, WriteOutcome.NOT_CONNECTED);
        String connectionId = connections.getConnectionIdByDeviceId(tracker.getDeviceId());
        if (connectionId == null) return new CommandWriteResult(attemptId, WriteOutcome.NOT_CONNECTED);
        return channel.write(new CommandWrite(attemptId, tracker.getDeviceId(), connectionId, expiresAt, encodedPayload));
    }
}
