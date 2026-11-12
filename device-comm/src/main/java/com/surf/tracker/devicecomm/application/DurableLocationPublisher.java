package com.surf.tracker.devicecomm.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.shared.domain.LocationPoint;
import com.surf.tracker.shared.events.LocationDataEvent;
import com.surf.tracker.shared.exception.InfrastructureException;
import com.surf.tracker.shared.pubsub.DomainEvent;
import com.surf.tracker.shared.pubsub.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;

/** Single-owner, bounded, fsync-before-return capture. At-least-once queue delivery. */
public final class DurableLocationPublisher implements EventPublisher, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DurableLocationPublisher.class);
    public static final String DESTINATION = "tracking.telemetry.locations";
    private final Path directory;
    private final ObjectMapper mapper;
    private final JmsTemplate queue;
    private final Clock clock;
    private final int maxFiles;
    private final long maxBytes;
    private final Object stateLock = new Object();
    private final Deque<Entry> pending = new ArrayDeque<>();
    private FileChannel ownerChannel;
    private FileLock ownerLock;
    private long bytes;
    private long sequence;
    private boolean closed;
    private boolean storageFailed;

    public DurableLocationPublisher(Path directory, ObjectMapper mapper, JmsTemplate queue,
                                    Clock clock, int maxFiles, long maxBytes) {
        this.directory = directory.toAbsolutePath().normalize();
        this.mapper = mapper;
        this.queue = queue;
        this.clock = clock;
        this.maxFiles = maxFiles;
        this.maxBytes = maxBytes;
        if (maxFiles < 1 || maxBytes < 1024) throw new IllegalArgumentException("Invalid spool capacity");
        try {
            Files.createDirectories(this.directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            if (Files.isSymbolicLink(this.directory)) throw new IOException("Spool directory must not be a symlink");
            ownerChannel = FileChannel.open(this.directory.resolve(".lock"), Set.of(StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            ownerLock = ownerChannel.tryLock();
            if (ownerLock == null) throw new IOException("Spool already has an owner");
            try (var paths = Files.list(this.directory)) {
                for (Path file : paths.sorted().toList()) {
                    if (file.getFileName().toString().equals(".lock")) continue;
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                            || !file.getFileName().toString().matches("[0-9]{20}-[0-9a-f-]{36}\\.json"))
                        throw new IOException("Uncommitted or unexpected spool record: " + file.getFileName());
                    long size = Files.size(file);
                    if (size > 32768) throw new IOException("Oversized spool record");
                    read(file); // Validate every retained record before accepting new packets.
                    pending.addLast(new Entry(file, size));
                    bytes += size;
                    sequence = Math.max(sequence, Long.parseLong(file.getFileName().toString().substring(0, 20)));
                }
            }
            if (pending.size() > maxFiles || bytes > maxBytes) throw new IOException("Retained spool exceeds configured capacity");
        } catch (Exception failure) {
            releaseOwner();
            throw new IllegalStateException("Cannot safely open telemetry spool " + this.directory, failure);
        }
    }

    @Override
    public void publish(DomainEvent source) {
        if (!(source instanceof LocationDataEvent event) || !event.getMetaData().isEmpty())
            throw new IllegalArgumentException("Spool accepts only the defined location envelope without dynamic metadata");
        PendingLocation record = new PendingLocation(1, event.getEventId(), event.getOccurredOn(),
                event.getDeviceId(), CapturedFix.from(event.getLocation()), event.getProtocol());
        record.validate();
        synchronized (stateLock) {
            if (closed || storageFailed) throw unavailable("Telemetry storage is not available", null);
            try {
                byte[] payload = mapper.writeValueAsBytes(record);
                if (payload.length > 32768 || pending.size() >= maxFiles || bytes + payload.length > maxBytes)
                    throw unavailable("Telemetry spool is full; retained packets were not evicted", null);
                sequence = Math.max(clock.millis(), Math.addExact(sequence, 1));
                String name = String.format(java.util.Locale.ROOT, "%020d-%s", sequence, record.eventId());
                Path temporary = directory.resolve(name + ".tmp");
                Path ready = directory.resolve(name + ".json");
                try (var channel = FileChannel.open(temporary, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                    var buffer = ByteBuffer.wrap(payload);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                Files.move(temporary, ready, StandardCopyOption.ATOMIC_MOVE);
                forceDirectory();
                pending.addLast(new Entry(ready, payload.length));
                bytes += payload.length;
            } catch (IOException failure) {
                storageFailed = true; // Preserve evidence; never skip a questionable record.
                throw unavailable("Telemetry capture failed; inspect retained spool before restarting", failure);
            }
        }
    }

    @Scheduled(fixedDelayString = "${tracking.telemetry.spool.poll-delay-ms:1000}")
    public synchronized void relayPending() {
        for (int sent = 0; sent < 100; sent++) {
            Entry entry;
            synchronized (stateLock) {
                if (closed || storageFailed || pending.isEmpty()) return;
                entry = pending.getFirst();
            }
            try {
                PendingLocation record = read(entry.path());
                // Transactional persistent JmsTemplate commit must complete before deletion.
                queue.convertAndSend(DESTINATION, record.toEvent());
                synchronized (stateLock) {
                    Files.delete(entry.path());
                    forceDirectory();
                    pending.removeFirst();
                    bytes -= entry.bytes();
                }
            } catch (IOException failure) {
                synchronized (stateLock) { storageFailed = true; }
                log.error("Telemetry spool storage failure; retained records require inspection", failure);
                return;
            } catch (RuntimeException failure) {
                log.warn("Telemetry queue unavailable; {} captured packets retained", pendingCount());
                return; // Retry the same envelope on the next scheduled attempt.
            }
        }
    }

    public int pendingCount() { synchronized (stateLock) { return pending.size(); } }

    private PendingLocation read(Path path) throws IOException {
        PendingLocation record = mapper.readValue(Files.readAllBytes(path), PendingLocation.class);
        try { record.validate(); }
        catch (RuntimeException malformed) { throw new IOException("Invalid retained telemetry record", malformed); }
        return record;
    }

    private void forceDirectory() throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }

    @Override
    public synchronized void close() {
        synchronized (stateLock) { closed = true; }
        releaseOwner();
    }

    private void releaseOwner() {
        try { if (ownerLock != null) ownerLock.close(); } catch (IOException ignored) { }
        try { if (ownerChannel != null) ownerChannel.close(); } catch (IOException ignored) { }
    }

    private static InfrastructureException unavailable(String message, Throwable failure) {
        return new InfrastructureException("TELEMETRY_CAPTURE_UNAVAILABLE", message, failure);
    }

    private record Entry(Path path, long bytes) { }

    private record PendingLocation(int version, String eventId, Instant occurredOn,
                                   String deviceId, CapturedFix location, String protocol) {
        void validate() {
            if (version != 1 || occurredOn == null || deviceId == null || !deviceId.matches("[0-9]{10,15}")
                    || protocol == null || protocol.isBlank() || protocol.length() > 50
                    || location == null || !location.toLocation().isValid()
                    || !Float.isFinite(location.speedKmh())) throw new IllegalArgumentException("Invalid location capture");
            UUID.fromString(eventId);
        }
        LocationDataEvent toEvent() {
            return new LocationDataEvent(Clock.fixed(occurredOn, java.time.ZoneOffset.UTC), UUID.fromString(eventId),
                    deviceId, location.toLocation(), protocol, null);
        }
    }

    // Store only the versioned wire fields, not LocationPoint's computed bean properties.
    private record CapturedFix(double latitude, double longitude, float speedKmh, Instant timestamp) {
        static CapturedFix from(LocationPoint point) {
            if (point == null) throw new IllegalArgumentException("GPS fix is required");
            return new CapturedFix(point.latitude(), point.longitude(), point.speedKmh(), point.timestamp());
        }
        LocationPoint toLocation() { return new LocationPoint(latitude, longitude, speedKmh, timestamp); }
    }
}
