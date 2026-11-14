package com.surf.tracker.devicecomm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.pipeline.ConnectionSession;
import com.surf.tracker.devicecomm.pipeline.IngestPipeline;
import com.surf.tracker.devicecomm.pipeline.ProtocolRegistry;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.FramingSpec;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import com.surf.tracker.shared.security.PairingToken;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.WebsocketServerSpec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Browser/phone listener. Serves the tracking page and accepts WebSocket telemetry, feeding the same
 * {@link IngestPipeline} flow as TCP devices. Disabled unless {@code tracking.phone.enabled=true}.
 *
 * <p>Protocol: the client's first message is {@code {"token":"<pairing token>"}}; once verified, every
 * later message is one fix and is stamped with the token's device id before it reaches the pipeline.
 * <p><b>HTTPS:</b> this listener speaks plain HTTP/WS. Browsers expose geolocation only on secure origins, so a TLS
 * proxy must front it in any real deployment. {@code http://localhost} is exempt (same-machine dev); a phone needs
 * an https tunnel or proxy. Exact steps, proxy snippets and troubleshooting: {@code device-comm/PHONE-TRACKING.md}.
 */
@Service
public class PhoneIngestServer {
    private static final Logger logger = LoggerFactory.getLogger(PhoneIngestServer.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_MESSAGE_BYTES = 2048;

    private final IngestPipeline pipeline;
    private final ProtocolRegistry registry;
    private final RedisConnectionTracker connectionTracker;
    private final Clock clock;

    @Value("${tracking.phone.enabled:false}")
    private boolean enabled;
    @Value("${tracking.phone.host:0.0.0.0}")
    private String host = "0.0.0.0";
    @Value("${tracking.phone.port:8891}")
    private int port;
    @Value("${tracking.phone.pairing-secret:}")
    private String pairingSecret;
    @Value("${tracking.phone.read-timeout:120}")
    private int readTimeoutSeconds;

    private byte[] secret;
    private String page;
    private DisposableServer server;

    public PhoneIngestServer(@Qualifier("webSocketIngestPipeline") IngestPipeline pipeline,
                             @Qualifier("webSocketProtocolRegistry") ProtocolRegistry registry,
                             RedisConnectionTracker connectionTracker, Clock clock) {
        this.pipeline = pipeline;
        this.registry = registry;
        this.connectionTracker = connectionTracker;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws IOException {
        if (!enabled) return;
        secret = pairingSecret.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32)
            throw new IllegalStateException("tracking.phone.pairing-secret must be at least 32 bytes when the phone listener is enabled");
        if (!(registry.framing() instanceof FramingSpec.Preframed))
            throw new IllegalStateException("The phone listener requires pre-framed protocols");
        page = new String(new ClassPathResource("phone/index.html").getContentAsByteArray(), StandardCharsets.UTF_8);
        server = HttpServer.create().host(host).port(port)
                .route(routes -> routes
                        .get("/", (request, response) -> response
                                .header("Content-Type", "text/html; charset=utf-8")
                                .header("Cache-Control", "no-store")
                                .header("Referrer-Policy", "no-referrer")
                                .header("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; "
                                        + "style-src 'unsafe-inline'; connect-src 'self' ws: wss:; base-uri 'none'; form-action 'none'")
                                .sendString(Mono.just(page)))
                        .ws("/ingest", this::handleSocket,
                                WebsocketServerSpec.builder().maxFramePayloadLength(MAX_MESSAGE_BYTES).build()))
                .bindNow();
        logger.info("Phone ingest listening on {}:{} (plain HTTP; front with TLS, see PHONE-TRACKING.md)", host, port);
    }

    public void stop() {
        if (server != null && !server.isDisposed()) server.disposeNow();
    }

    private org.reactivestreams.Publisher<Void> handleSocket(reactor.netty.http.server.WebsocketInbound inbound,
                                                             reactor.netty.http.server.WebsocketOutbound outbound) {
        String connectionId = "ws-" + UUID.randomUUID();
        ConnectionSession session = new ConnectionSession();
        AtomicReference<String> authenticated = new AtomicReference<>();
        Sinks.Many<String> replies = Sinks.many().unicast().onBackpressureBuffer();

        inbound.withConnection(connection -> {
            InetSocketAddress remote = (InetSocketAddress) connection.address();
            connectionTracker.registerConnection(connectionId, "unknown",
                    remote != null ? remote.getAddress().getHostAddress() : "unknown", connection);
            connection.addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS));
        });

        Mono<Void> processing = inbound.receive().asByteArray()
                .concatMap(message -> Mono.fromCallable(() -> respond(message, connectionId, session, authenticated))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(reply -> reply.isEmpty() ? Mono.<Void>empty() : Mono.fromRunnable(() -> replies.tryEmitNext(reply))), 1)
                .doOnError(error -> logger.warn("Phone connection {} failed: {}", connectionId, error.getClass().getSimpleName()))
                .doFinally(signal -> {
                    replies.tryEmitComplete();
                    try { connectionTracker.removeConnection(connectionId); }
                    catch (RuntimeException failure) { logger.error("Error cleaning up connection {}", connectionId, failure); }
                })
                .then();
        return Mono.when(processing, outbound.sendString(replies.asFlux()).then());
    }

    /** @return a JSON reply for the client, or "" for none. Unauthenticated peers get one error and are closed. */
    private String respond(byte[] message, String connectionId, ConnectionSession session,
                           AtomicReference<String> authenticated) {
        String deviceId = authenticated.get();
        if (deviceId == null) {
            String proven = verify(message);
            if (proven == null) {
                logger.warn("Phone connection {} rejected: invalid pairing token", connectionId);
                throw new IllegalStateException("Pairing rejected"); // closes the socket
            }
            authenticated.set(proven);
            connectionTracker.updateConnectionDevice(connectionId, proven);
            return "{\"type\":\"ready\"}";
        }
        try {
            pipeline.ingest(new Frame(message, deviceId), clock.instant(), session);
            connectionTracker.updateLastSeen(connectionId);
            return "";
        } catch (ProtocolException rejected) {
            logger.warn("Rejected phone frame on {}: {}", connectionId, rejected.getMessage());
            return "{\"type\":\"rejected\",\"code\":\"" + rejected.getErrorCode() + "\"}";
        }
        // Publication failures propagate so the socket closes and the client reconnects, never silently drops fixes.
    }

    private String verify(byte[] message) {
        try {
            JsonNode root = JSON.readTree(message);
            JsonNode token = root == null ? null : root.get("token");
            if (token == null || !token.isTextual()) return null;
            return PairingToken.verify(secret, token.asText(), clock.instant()).orElse(null);
        } catch (IOException malformed) {
            return null;
        }
    }
}
