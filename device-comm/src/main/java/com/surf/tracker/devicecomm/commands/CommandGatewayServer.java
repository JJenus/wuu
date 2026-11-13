package com.surf.tracker.devicecomm.commands;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.surf.tracker.devicecomm.service.ReactiveTcpServer;
import com.surf.tracker.shared.commands.CommandWrite;
import com.surf.tracker.shared.commands.CommandWriteResult;
import com.surf.tracker.shared.commands.WriteOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;

/** Private service-to-service endpoint, not part of the public business API. */
@Component
@ConditionalOnProperty(name = "tracking.commands.gateway.enabled", havingValue = "true")
public class CommandGatewayServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 8192;
    private final ReactiveTcpServer sockets;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String host;
    private final int port;
    private final byte[] secret;
    private final Path directory;
    private final int maxReceipts;
    private CommandWriteJournal journal;
    private DisposableServer server;

    public CommandGatewayServer(ReactiveTcpServer sockets, ObjectMapper mapper, Clock clock,
            @Value("${tracking.commands.gateway.host:127.0.0.1}") String host,
            @Value("${tracking.commands.gateway.port:8890}") int port,
            @Value("${tracking.commands.gateway.secret:}") String secret,
            @Value("${tracking.commands.gateway.journal-directory:./data/command-writes}") String directory,
            @Value("${tracking.commands.gateway.max-receipts:10000}") int maxReceipts) {
        if (secret == null || !secret.matches("[!-~]{32,256}"))
            throw new IllegalArgumentException("Enabled command gateway requires a 32–256 character secret");
        this.sockets = sockets;
        this.mapper = mapper.copy().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.clock = clock; this.host = host; this.port = port;
        this.secret = secret.getBytes(StandardCharsets.US_ASCII);
        this.directory = Path.of(directory); this.maxReceipts = maxReceipts;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() throws IOException {
        if (server != null) return;
        journal = new CommandWriteJournal(directory, mapper, maxReceipts);
        try {
            server = HttpServer.create().host(host).port(port)
                .httpRequestDecoder(spec -> spec.maxInitialLineLength(256).maxHeaderSize(2048))
                .doOnConnection(connection -> connection.addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(5)))
                .handle(this::handle).bindNow();
        } catch (RuntimeException failure) { journal.close(); journal = null; throw failure; }
    }

    private Mono<Void> handle(HttpServerRequest request, HttpServerResponse response) {
        response.header("Cache-Control", "no-store").header("Content-Type", "application/json");
        if (!"POST".equals(request.method().name()) || !"/internal/command-writes".equals(request.uri()))
            return response.status(404).send().then();
        var credentials = request.requestHeaders().getAll("X-Command-Secret");
        if (credentials.size() != 1 || !MessageDigest.isEqual(secret, credentials.getFirst().getBytes(StandardCharsets.UTF_8)))
            return response.status(401).send().then();
        String contentType = request.requestHeaders().get("Content-Type", "");
        if (!"application/json".equalsIgnoreCase(contentType.split(";", 2)[0].trim())
            || request.requestHeaders().contains("Content-Encoding"))
            return response.status(415).send().then();
        return request.receive().asByteArray().reduce(new ByteArrayOutputStream(), (buffer, bytes) -> {
            if (buffer.size() + bytes.length > MAX_BODY_BYTES) throw new BodyTooLargeException();
            buffer.writeBytes(bytes); return buffer;
        }).flatMap(buffer -> Mono.fromCallable(() -> {
            CommandWrite command = mapper.readValue(buffer.toByteArray(), CommandWrite.class);
            WriteOutcome outcome;
            if (!command.expiresAt().isAfter(clock.instant())) outcome = WriteOutcome.EXPIRED;
            else if (command.expiresAt().isAfter(clock.instant().plusSeconds(30))) outcome = WriteOutcome.REJECTED;
            else outcome = journal.execute(command, () -> sockets.writeCommand(command));
            return mapper.writeValueAsString(new CommandWriteResult(command.attemptId(), outcome));
        }).subscribeOn(Schedulers.boundedElastic())
            .flatMap(body -> response.status(200).sendString(Mono.just(body)).then()))
            .onErrorResume(BodyTooLargeException.class, error -> response.status(413).send().then())
            .onErrorResume(com.fasterxml.jackson.core.JacksonException.class, error -> response.status(400).send().then())
            .onErrorResume(IllegalArgumentException.class, error -> response.status(400).send().then())
            // Storage or socket exceptions must never imply a successful write or trigger a resend.
            .onErrorResume(error -> response.status(503).send().then());
    }

    public int port() { return server == null ? -1 : server.port(); }
    @jakarta.annotation.PreDestroy
    @Override public synchronized void close() throws IOException {
        if (server != null) { server.disposeNow(); server = null; }
        if (journal != null) { journal.close(); journal = null; }
    }
    private static final class BodyTooLargeException extends RuntimeException {}
}
