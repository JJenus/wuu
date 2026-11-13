package com.surf.tracker.devicecomm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.surf.tracker.devicecomm.service.ReactiveTcpServer;
import com.surf.tracker.shared.pubsub.ArtemisEventPublisher;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import com.surf.tracker.shared.redis.RedisJsonSerializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Clock;
import com.surf.tracker.devicecomm.publish.DurableLocationPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.nio.file.Path;

@SpringBootApplication
@EnableScheduling
@Import({RedisConnectionTracker.class, ArtemisEventPublisher.class})
public class DeviceCommApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeviceCommApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Primary
    public DurableLocationPublisher telemetryPublisher(ObjectMapper mapper,
            @Qualifier("queueJmsTemplate") JmsTemplate queue, Clock clock,
            @Value("${tracking.telemetry.spool.directory:./data/telemetry-spool}") String directory,
            @Value("${tracking.telemetry.spool.max-files:10000}") int maxFiles,
            @Value("${tracking.telemetry.spool.max-bytes:67108864}") long maxBytes) {
        return new DurableLocationPublisher(Path.of(directory), mapper, queue, clock, maxFiles, maxBytes);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                      ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        var serializer = RedisJsonSerializer.create(objectMapper);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        return template;
    }

    @EventListener
    public void stopTcpServer(ContextClosedEvent event) {
        event.getApplicationContext().getBean(ReactiveTcpServer.class).stop();
    }
}
