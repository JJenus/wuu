package com.surf.tracker.devicecomm.commands;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.shared.commands.CommandWrite;
import com.surf.tracker.shared.commands.WriteOutcome;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Supplier;
import static java.nio.file.StandardOpenOption.*;

/** Private persistent attempt ledger: fsync intent BEFORE the irreversible socket write. */
public final class CommandWriteJournal implements AutoCloseable {
    private final Path directory;
    private final ObjectMapper mapper;
    private final int maxReceipts;
    private final FileChannel owner;
    private final FileLock lock;
    public record Entry(String fingerprint, WriteOutcome outcome) {}

    public CommandWriteJournal(Path directory, ObjectMapper mapper, int maxReceipts) throws IOException {
        if (maxReceipts < 1) throw new IllegalArgumentException("Command journal must have a positive bound");
        this.directory = directory.toAbsolutePath().normalize(); this.mapper = mapper; this.maxReceipts = maxReceipts;
        Files.createDirectories(this.directory, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if (Files.isSymbolicLink(this.directory)) throw new IOException("Command journal cannot be a symlink");
        Files.setPosixFilePermissions(this.directory, PosixFilePermissions.fromString("rwx------"));
        owner = FileChannel.open(this.directory.resolve(".owner"), CREATE, WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            lock = owner.tryLock();
            if (lock == null) throw new IOException("Command journal is already in use");
        } catch (IOException | RuntimeException failure) { owner.close(); throw failure; }
    }

    public synchronized WriteOutcome execute(CommandWrite request, Supplier<WriteOutcome> write) throws IOException {
        Path receipt = directory.resolve(request.attemptId() + ".json");
        String fingerprint;
        try { fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(request))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        if (Files.exists(receipt, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(receipt) || Files.size(receipt) > 1024) return WriteOutcome.UNKNOWN;
            Entry saved;
            try { saved = mapper.readValue(Files.readAllBytes(receipt), Entry.class); }
            catch (IOException corrupt) { return WriteOutcome.UNKNOWN; }
            if (!fingerprint.equals(saved.fingerprint())) return WriteOutcome.ID_CONFLICT;
            return saved.outcome() == null ? WriteOutcome.UNKNOWN : saved.outcome();
        }
        try (var paths = Files.list(directory)) {
            if (paths.filter(path -> path.toString().endsWith(".json")).count() >= maxReceipts) return WriteOutcome.REJECTED;
        }
        persist(receipt, new Entry(fingerprint, WriteOutcome.UNKNOWN), true);
        WriteOutcome result = write.get();
        // A crash or failure before this receipt is durable leaves UNKNOWN and never resends.
        persist(receipt, new Entry(fingerprint, result), false);
        return result;
    }

    private void persist(Path path, Entry value, boolean create) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        try (var channel = FileChannel.open(path, create ? CREATE_NEW : WRITE, WRITE, LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.truncate(bytes.length); channel.force(true);
        }
        try (var parent = FileChannel.open(directory, READ)) { parent.force(true); }
    }
    @Override public void close() throws IOException { lock.release(); owner.close(); }
}
