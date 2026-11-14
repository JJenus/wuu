package com.surf.tracker.devicecomm.config;

import com.surf.tracker.devicecomm.pipeline.IngestPipeline;
import com.surf.tracker.devicecomm.pipeline.ProtocolRegistry;
import com.surf.tracker.devicecomm.protocol.DeviceProtocol;
import com.surf.tracker.devicecomm.protocol.Transport;
import com.surf.tracker.devicecomm.protocol.autoseeker.AutoseekerProtocol;
import com.surf.tracker.devicecomm.protocol.gt06.Gt06Protocol;
import com.surf.tracker.devicecomm.protocol.phone.PhoneProtocol;
import com.surf.tracker.shared.pubsub.EventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

/**
 * One bean per supported protocol. A new protocol adds a bean here (or is a {@code @Component} itself);
 * the registry and pipeline discover it without further changes.
 */
@Configuration
public class ProtocolConfig {

    @Bean
    public AutoseekerProtocol autoseekerProtocol(
            @Value("${tracking.tcp.autoseeker.timestamp-zone:UTC}") String timestampZone,
            @Value("${tracking.tcp.server.max-message-length:1024}") int maxMessageLength) {
        return new AutoseekerProtocol(ZoneId.of(timestampZone), maxMessageLength);
    }

    @Bean
    public Gt06Protocol gt06Protocol(
            @Value("${tracking.tcp.gt06.timestamp-zone:UTC}") String timestampZone,
            @Value("${tracking.tcp.server.max-message-length:1024}") int maxMessageLength) {
        return new Gt06Protocol(ZoneId.of(timestampZone), maxMessageLength);
    }

    @Bean
    public PhoneProtocol phoneProtocol(
            @Value("${tracking.phone.max-accuracy-meters:100}") int maxAccuracyMeters) {
        return new PhoneProtocol(maxAccuracyMeters);
    }

    /** One registry and pipeline per transport: protocols on a listener must share framing and authentication. */
    @Bean
    public ProtocolRegistry tcpProtocolRegistry(List<DeviceProtocol> protocols) {
        return ProtocolRegistry.forTransport(protocols, Transport.TCP);
    }

    @Bean
    public ProtocolRegistry webSocketProtocolRegistry(List<DeviceProtocol> protocols) {
        return ProtocolRegistry.forTransport(protocols, Transport.WEBSOCKET);
    }

    @Bean
    public IngestPipeline tcpIngestPipeline(@org.springframework.beans.factory.annotation.Qualifier("tcpProtocolRegistry") ProtocolRegistry registry,
                                            EventPublisher publisher, Clock clock) {
        return new IngestPipeline(registry, publisher, clock);
    }

    @Bean
    public IngestPipeline webSocketIngestPipeline(@org.springframework.beans.factory.annotation.Qualifier("webSocketProtocolRegistry") ProtocolRegistry registry,
                                                  EventPublisher publisher, Clock clock) {
        return new IngestPipeline(registry, publisher, clock);
    }
}
