package com.surf.tracker.devicecomm.service;

import com.surf.tracker.devicecomm.application.DeviceDataProcessor;
import com.surf.tracker.devicecomm.domain.DeviceDataPacket;
import com.surf.tracker.shared.domain.ConnectionInfo;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;
import reactor.util.concurrent.Queues;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ReactiveTcpServer {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveTcpServer.class);
    private static final AtomicInteger CONNECTION_COUNTER = new AtomicInteger(0);

    private final DeviceDataProcessor deviceDataProcessor;
    private final RedisConnectionTracker connectionTracker;

    @Value("${tracking.tcp.server.port:8888}")
    private int tcpPort;

    @Value("${tracking.tcp.server.message-delimiter:#}")
    private String messageDelimiter;

    @Value("${tracking.tcp.server.max-message-length:1024}")
    private int maxMessageLength;

    @Value("${tracking.tcp.server.read-timeout:300}")
    private int readTimeoutSeconds;

    private DisposableServer server;
    private volatile boolean running = false;

    public ReactiveTcpServer(DeviceDataProcessor deviceDataProcessor,
                             RedisConnectionTracker connectionTracker) {
        this.deviceDataProcessor = deviceDataProcessor;
        this.connectionTracker = connectionTracker;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (running) {
            logger.warn("TCP Server is already running");
            return;
        }

        logger.info("Starting Reactive TCP Server on port {}", tcpPort);

        try {
            server = TcpServer.create()
                    .port(tcpPort)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .doOnConnection(this::handleNewConnection)
                    .doOnBind(bind -> logger.info("TCP Server binding to port {}", tcpPort))
                    .doOnBound(bound -> logger.info("TCP Server successfully bound"))
                    .doOnUnbound(unbound -> logger.info("TCP Server unbound"))
                    .wiretap("reactor.netty.tcp.TcpServer", LogLevel.DEBUG)
                    .handle((inbound, outbound) -> {
                        String connectionId = "conn-" + CONNECTION_COUNTER.incrementAndGet();

                        final String[] clientIp = new String[]{"unknown"};
                        final Connection[] nettyConnection = new Connection[1];

                        // Get connection and register
                        inbound.withConnection(conn -> {
                            nettyConnection[0] = conn;

                            InetSocketAddress remoteAddress = (InetSocketAddress) conn.address();
                            clientIp[0] = remoteAddress != null ?
                                    remoteAddress.getAddress().getHostAddress() : "unknown";

                            // Register connection in tracker
                            connectionTracker.registerConnection(connectionId, "unknown", clientIp[0], conn);

                            // Add timeout handler
                            conn.addHandlerLast(new ReadTimeoutHandler(readTimeoutSeconds, TimeUnit.SECONDS));

                            logger.info("Connection {} established from {}", connectionId, clientIp[0]);
                        });

                        // Process incoming data with proper framing
                        return inbound.receive()
                                .asByteArray()
                                .transform(this::frameWithDelimiter)
                                .doOnNext(message -> processRawMessage(message, connectionId, clientIp[0]))
                                .doOnError(error -> handleConnectionError(error, connectionId))
                                .doFinally(signal -> cleanupConnection(connectionId))
                                .then();
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

        connection.onDispose(() -> {
            logger.debug("Connection from {}:{} closed", clientIp, clientPort);
        });
    }

    private Flux<String> frameWithDelimiter(Flux<byte[]> dataStream) {
        return dataStream
                .concatMapIterable(bytes -> {
                    // Convert byte array to a list of ByteBuffer for processing
                    List<ByteBuffer> list = new ArrayList<>();
                    list.add(ByteBuffer.wrap(bytes));
                    return list;
                })
                .transform(flux -> Flux.create(sink -> {
                    ByteBuffer buffer = ByteBuffer.allocate(maxMessageLength);
                    flux.subscribe(
                            byteBuffer -> {
                                try {
                                    while (byteBuffer.hasRemaining()) {
                                        byte b = byteBuffer.get();

                                        // Add byte to buffer (INCLUDING the delimiter '#')
                                        if (buffer.position() >= maxMessageLength) {
                                            sink.error(new RuntimeException(
                                                    "Message exceeds maximum length of " + maxMessageLength + " bytes. " +
                                                            "Current buffer: " + new String(buffer.array(), 0, buffer.position(), StandardCharsets.US_ASCII)));
                                            return;
                                        }
                                        buffer.put(b);

                                        // Check if we just added the delimiter '#'
                                        if (b == (byte) '#') {
                                            buffer.flip();
                                            byte[] messageBytes = new byte[buffer.remaining()];
                                            buffer.get(messageBytes);

                                            // Convert to string WITH the delimiter included
                                            String fullMessage = new String(messageBytes, StandardCharsets.US_ASCII);

                                            // Send to parser
                                            sink.next(fullMessage);

                                            // Clear for next message
                                            buffer.clear();
                                        }
                                    }
                                } catch (Exception e) {
                                    sink.error(new RuntimeException("Error processing byte buffer: " + e.getMessage(), e));
                                }
                            },
                            error -> {
                                // Log connection error but don't fail the sink
                                logger.warn("Connection stream error during framing: {}", error.getMessage());
                                // Complete the sink so connection can be cleaned up
                                sink.complete();
                            },
                            () -> {
                                // Handle any incomplete message when stream ends
                                if (buffer.position() > 0) {
                                    logger.debug("Connection closed with {} bytes in buffer (incomplete message)",
                                            buffer.position());
                                }
                                sink.complete();
                            }
                    );
                }, FluxSink.OverflowStrategy.BUFFER));
    }

    private void processRawMessage(String rawMessage, String connectionId, String clientIp) {
        try {
            if (rawMessage == null || rawMessage.trim().isEmpty()) {
                logger.warn("Empty message from connection {}", connectionId);
                return;
            }

            // Log raw message for debugging
            logger.debug("Received raw message ({} chars) from connection {}: {}",
                    rawMessage.length(), connectionId, rawMessage);

            // Ensure message ends with delimiter for parser
            String messageForParser = rawMessage;
            if (!rawMessage.endsWith(messageDelimiter)) {
                messageForParser = rawMessage + messageDelimiter;
                logger.debug("Added missing delimiter to message from connection {}", connectionId);
            }

            // Extract device ID
            String deviceId = extractDeviceIdFromRawMessage(messageForParser);

            // Update connection tracker
            if (!"unknown".equals(deviceId)) {
                connectionTracker.updateConnectionDevice(connectionId, deviceId);
                logger.debug("Updated connection {} with device ID {}", connectionId, deviceId);
            }

            // Create and process packet
            DeviceDataPacket packet = new DeviceDataPacket(
                    deviceId,
                    messageForParser,
                    Instant.now(),
                    clientIp
            );

            // Process asynchronously
            Mono.fromRunnable(() -> {
                        try {
                            deviceDataProcessor.processDeviceData(packet);
                        } catch (Exception e) {
                            logger.error("Error in device data processor for connection {}",
                                    connectionId, e);
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(
                            null,
                            error -> logger.error("Subscription error for connection {}",
                                    connectionId, error)
                    );

            // Update last seen
            connectionTracker.updateLastSeen(connectionId);

        } catch (Exception e) {
            logger.error("Error processing message from connection {}: {}",
                    connectionId, e.getMessage());
        }
    }

    private String extractDeviceIdFromRawMessage(String rawMessage) {
        try {
            if (rawMessage != null && rawMessage.startsWith("*")) {
                // Remove the delimiter for parsing
                String cleanMessage = rawMessage;
                if (cleanMessage.endsWith(messageDelimiter)) {
                    cleanMessage = cleanMessage.substring(0, cleanMessage.length() - 1);
                }

                String[] parts = cleanMessage.split(",", 3);
                if (parts.length >= 2) {
                    String deviceId = parts[1].trim();
                    // Validate device ID format (IMEI-like)
                    if (deviceId.matches("[0-9]{10,15}")) {
                        return deviceId;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to extract device ID from message: {}",
                    rawMessage != null ? rawMessage.substring(0, Math.min(rawMessage.length(), 100)) : "null");
        }
        return "unknown";
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

    public Mono<Boolean> sendCommandToDevice(String deviceId, String command) {
        return Mono.fromCallable(() -> {
            try {
                ConnectionInfo connectionInfo = connectionTracker.getConnectionByDeviceId(deviceId);

                if (connectionInfo == null) {
                    logger.warn("No active connection for device {}", deviceId);
                    return false;
                }

                Connection nettyConnection = connectionTracker.getLiveConnection(connectionInfo.getConnectionId());
                if (nettyConnection == null || nettyConnection.isDisposed()) {
                    logger.warn("Connection for device {} is disposed", deviceId);
                    connectionTracker.removeConnection(connectionInfo.getConnectionId());
                    return false;
                }

                // Ensure command ends with delimiter
                String fullCommand = command;
                if (!command.endsWith(messageDelimiter)) {
                    fullCommand = command + messageDelimiter;
                }

                // Send command
                nettyConnection.outbound()
                        .sendString(Mono.just(fullCommand))
                        .then()
                        .block(Duration.ofSeconds(10));

                logger.info("Command sent to device {}: {}", deviceId, command);
                return true;

            } catch (Exception e) {
                logger.error("Error sending command to device {}", deviceId, e);
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}