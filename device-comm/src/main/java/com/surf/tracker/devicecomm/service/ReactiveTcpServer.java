package com.surf.tracker.devicecomm.service;

import com.surf.tracker.devicecomm.exception.ProtocolException;
import com.surf.tracker.devicecomm.pipeline.ConnectionSession;
import com.surf.tracker.devicecomm.pipeline.IngestPipeline;
import com.surf.tracker.devicecomm.pipeline.ProtocolRegistry;
import com.surf.tracker.devicecomm.protocol.DelimitedFrameDecoder;
import com.surf.tracker.devicecomm.protocol.Frame;
import com.surf.tracker.devicecomm.protocol.FramingSpec;
import com.surf.tracker.shared.commands.CommandWrite;
import com.surf.tracker.shared.commands.WriteOutcome;
import com.surf.tracker.shared.domain.ConnectionInfo;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Protocol-agnostic TCP listener: cuts the stream into frames using the registry's framing and hands
 * each frame to the {@link IngestPipeline}. It knows nothing about any device protocol.
 */
@Service
public class ReactiveTcpServer {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveTcpServer.class);
    private static final AttributeKey<ConnectionSession> SESSION = AttributeKey.valueOf("tracker.session");

    private final IngestPipeline pipeline;
    private final ProtocolRegistry registry;
    private final RedisConnectionTracker connectionTracker;

    @Value("${tracking.tcp.server.port:8888}")
    private int tcpPort;

    @Value("${tracking.tcp.server.host:0.0.0.0}")
    private String tcpHost = "0.0.0.0";

    @Value("${tracking.tcp.server.read-timeout:300}")
    private int readTimeoutSeconds;

    @Value("${tracking.tcp.server.wiretap-enabled:false}")
    private boolean wiretapEnabled;

    @Value("${tracking.tcp.server.max-pending-frames:256}")
    private int maxPendingFrames = 256;

    private DisposableServer server;
    private FramingSpec framing;
    private volatile boolean running = false;

    public ReactiveTcpServer(@org.springframework.beans.factory.annotation.Qualifier("tcpIngestPipeline") IngestPipeline pipeline,
                             @org.springframework.beans.factory.annotation.Qualifier("tcpProtocolRegistry") ProtocolRegistry registry,
                             RedisConnectionTracker connectionTracker) {
        this.pipeline = pipeline;
        this.registry = registry;
        this.connectionTracker = connectionTracker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (running) {
            logger.warn("TCP Server is already running");
            return;
        }

        logger.info("Starting Reactive TCP Server on port {}", tcpPort);
        if (maxPendingFrames < 1 || maxPendingFrames > 4096)
            throw new IllegalArgumentException("TCP pending-frame bound must be between 1 and 4096");
        framing = registry.framing();

        try {
            TcpServer tcp = TcpServer.create()
                    .host(tcpHost)
                    .port(tcpPort)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .doOnConnection(this::handleNewConnection)
                    .doOnBind(bind -> logger.info("TCP Server binding to port {}", tcpPort))
                    .doOnBound(bound -> logger.info("TCP Server successfully bound"))
                    .doOnUnbound(unbound -> logger.info("TCP Server unbound"));
            if (wiretapEnabled) {
                // Opt-in payload tracing is unsafe in normal production because frames
                // contain device identifiers and coordinates.
                tcp = tcp.wiretap("reactor.netty.tcp.TcpServer", LogLevel.DEBUG,
                        reactor.netty.transport.logging.AdvancedByteBufFormat.SIMPLE);
            }
            server = tcp
                    .handle((inbound, outbound) -> {
                        String connectionId = "conn-" + UUID.randomUUID();
                        ConnectionSession session = new ConnectionSession();
                        final String[] clientIp = new String[]{"unknown"};

                        inbound.withConnection(conn -> {
                            conn.channel().attr(SESSION).set(session);

                            InetSocketAddress remoteAddress = (InetSocketAddress) conn.address();
                            clientIp[0] = remoteAddress != null ?
                                    remoteAddress.getAddress().getHostAddress() : "unknown";

                            connectionTracker.registerConnection(connectionId, "unknown", clientIp[0], conn);
                            conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS));

                            logger.info("Connection {} established from {}", connectionId, clientIp[0]);
                        });

                        return inbound.receive()
                                .asByteArray()
                                .transform(this::toFrames)
                                .onBackpressureBuffer(maxPendingFrames)
                                // Complete each blocking decode/publication before the next frame on
                                // this connection. Detached workers can publish GPS fixes out of order.
                                .concatMapDelayError(frame -> Mono.fromRunnable(() ->
                                                processFrame(frame, connectionId, session))
                                        .subscribeOn(Schedulers.boundedElastic()), false, 1)
                                .doOnError(error -> handleConnectionError(error, connectionId))
                                .doFinally(signal -> cleanupConnection(connectionId))
                                .then()
                                // Once started, complete accepted frames even if the peer closes.
                                // Cancellation must not interrupt FileChannel fsync during capture.
                                .cache();
                    })
                    .bindNow();

            running = true;
            logger.info("TCP Server started successfully on port {}", tcpPort);

        } catch (Exception e) {
            logger.error("Failed to start TCP Server on port {}", tcpPort, e);
            throw new RuntimeException("Failed to start TCP Server", e);
        }
    }

    private void handleNewConnection(Connection connection) {
        InetSocketAddress remoteAddress = (InetSocketAddress) connection.address();
        String clientIp = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
        int clientPort = remoteAddress != null ? remoteAddress.getPort() : 0;

        logger.debug("New TCP connection from {}:{}", clientIp, clientPort);

        connection.onDispose(() -> logger.debug("Connection from {}:{} closed", clientIp, clientPort));
    }

    /** Fresh decoder per subscription, so framing state never leaks between connections. */
    private Flux<Frame> toFrames(Flux<byte[]> bytes) {
        return Flux.defer(() -> {
            DelimitedFrameDecoder decoder = switch (framing) {
                case FramingSpec.Delimited delimited -> new DelimitedFrameDecoder(delimited);
                case FramingSpec.Preframed ignored ->
                        throw new IllegalStateException("Pre-framed protocols belong on a message transport, not raw TCP");
            };
            return bytes.concatMapIterable(decoder::feed);
        });
    }

    private void processFrame(Frame frame, String connectionId, ConnectionSession session) {
        try {
            String deviceId = pipeline.ingest(frame, Instant.now(), session);
            // Only successfully decoded/published frames may claim device routing/activity.
            connectionTracker.updateConnectionDevice(connectionId, deviceId);
            connectionTracker.updateLastSeen(connectionId);
        } catch (ProtocolException rejected) {
            // Invalid frames are isolated; a later valid fix can still use this connection.
            logger.warn("Rejected device frame on connection {}: {}", connectionId, rejected.getMessage());
        } catch (RuntimeException failure) {
            // Publication/storage failure must reach Reactor/Netty so this connection
            // closes. Keeping it open silently drops subsequent device data.
            logger.error("Device intake failed on connection {}; closing connection", connectionId, failure);
            throw failure;
        }
    }

    private void handleConnectionError(Throwable error, String connectionId) {
        if (error instanceof io.netty.handler.timeout.ReadTimeoutException) {
            logger.warn("Read timeout for connection {}", connectionId);
        } else {
            logger.warn("Connection error for {}: {}", connectionId, error.getMessage());
        }
    }

    private void cleanupConnection(String connectionId) {
        try {
            connectionTracker.removeConnection(connectionId);
            logger.info("Cleaned up connection {}", connectionId);
        } catch (Exception e) {
            logger.error("Error cleaning up connection {}", connectionId, e);
        }
    }

    public void stop() {
        if (!running) {
            logger.warn("TCP Server is not running");
            return;
        }

        logger.info("Stopping TCP Server...");

        if (server != null && !server.isDisposed()) {
            server.disposeNow();
        }

        running = false;
        logger.info("TCP Server stopped");
    }

    public int getActiveConnectionCount() {
        return connectionTracker.getActiveConnectionCount();
    }

    /** Protocol-neutral write to one exact, previously identified live connection. */
    public WriteOutcome writeCommand(CommandWrite request) {
        if (!request.expiresAt().isAfter(Instant.now())) return WriteOutcome.EXPIRED;
        Connection live = connectionTracker.getLiveConnection(request.connectionId());
        if (live == null || live.isDisposed()) return WriteOutcome.NOT_CONNECTED;
        ConnectionSession session = live.channel().attr(SESSION).get();
        if (session == null || !request.deviceId().equals(session.boundDeviceId())) return WriteOutcome.STALE_CONNECTION;
        ConnectionInfo current = connectionTracker.getConnectionByDeviceId(request.deviceId());
        if (current == null || !request.connectionId().equals(current.getConnectionId())) return WriteOutcome.STALE_CONNECTION;
        try {
            // No text conversion, appended delimiter, protocol lookup or command-payload logging.
            live.outbound().sendByteArray(Mono.just(request.payload())).then().block(Duration.ofSeconds(5));
            return WriteOutcome.SENT;
        } catch (RuntimeException ambiguous) { return WriteOutcome.UNKNOWN; }
    }
}
