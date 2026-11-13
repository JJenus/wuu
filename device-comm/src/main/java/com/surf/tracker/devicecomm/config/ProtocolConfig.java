package com.surf.tracker.devicecomm.config;

import com.surf.tracker.devicecomm.protocol.autoseeker.AutoseekerProtocol;
import com.surf.tracker.devicecomm.protocol.gt06.Gt06Protocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

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
}
