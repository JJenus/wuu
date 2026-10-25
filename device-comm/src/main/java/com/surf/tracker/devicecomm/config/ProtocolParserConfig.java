package com.surf.tracker.devicecomm.config;

import com.surf.tracker.devicecomm.domain.ITrackerProtocolParser;
import com.surf.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
import com.surf.tracker.devicecomm.infrastructure.GT06ProtocolParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ProtocolParserConfig {
    
    @Bean
    public List<ITrackerProtocolParser> protocolParsers() {
        return List.of(
            new GT06ProtocolParser(),
            new AutoseekerProtocolParser()
        );
    }
}