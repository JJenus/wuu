package com.surf.tracker.core.config;

import com.surf.tracker.shared.pubsub.DeviceCommandBuilder;
import com.surf.tracker.shared.pubsub.DeviceCommandTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Configuration
public class DeviceCommandConfig {

    private static final Logger logger = LoggerFactory.getLogger(DeviceCommandConfig.class);

    @Bean
    public DeviceCommandTransport deviceCommandTransport() {
        return (deviceId, command) -> {
            logger.warn("Device commands are disabled; device-comm is deployed separately (deviceId={}, command={})", deviceId, command);
            return Mono.just(false);
        };
    }

    @Bean
    public List<DeviceCommandBuilder> deviceCommandBuilders() {
        return Collections.emptyList();
    }
}
