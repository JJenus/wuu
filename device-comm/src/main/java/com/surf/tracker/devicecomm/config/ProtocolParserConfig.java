package com.surf.tracker.devicecomm.config;

import com.surf.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import com.surf.tracker.devicecomm.infrastructure.GT06ProtocolParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class ProtocolParserConfig {
    
    @Bean
    public AutoseekerProtocolParser autoseekerProtocolParser(
            @Value("${tracking.tcp.autoseeker.timestamp-zone:UTC}") String timestampZone,
            @Value("${tracking.tcp.server.max-message-length:1024}") int maxMessageLength) {
        return new AutoseekerProtocolParser(ZoneId.of(timestampZone), maxMessageLength);
    }

    @Bean
    public GT06ProtocolParser gt06ProtocolParser() {
        return new GT06ProtocolParser();
    }
}
