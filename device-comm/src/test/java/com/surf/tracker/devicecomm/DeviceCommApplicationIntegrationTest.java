package com.surf.tracker.devicecomm;

import com.surf.tracker.devicecomm.application.DeviceDataProcessor;
import com.surf.tracker.devicecomm.service.ReactiveTcpServer;
import com.surf.tracker.shared.pubsub.ArtemisEventPublisher;
import com.surf.tracker.shared.pubsub.DeviceCommandTransport;
import com.surf.tracker.shared.pubsub.EventPublisher;
import com.surf.tracker.shared.redis.RedisConnectionTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class DeviceCommApplicationIntegrationTest {

    @Test
    void run_standaloneConfiguration_startsDeviceInfrastructureWithoutBusinessModules(CapturedOutput output) {
        // given
        var application = new SpringApplicationBuilder(DeviceCommApplication.class);

        // when
        try (var context = application.run("--tracking.tcp.server.port=0",
                "--spring.main.keep-alive=false")) {
            // then
            assertThat(context.getBean(DeviceDataProcessor.class)).isNotNull();
            assertThat(context.getBean(ReactiveTcpServer.class)).isNotNull();
            assertThat(context.getBean(DeviceCommandTransport.class)).isNotNull();
            assertThat(context.getBean(RedisConnectionTracker.class)).isNotNull();
            assertThat(context.getBean(EventPublisher.class)).isInstanceOf(ArtemisEventPublisher.class);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(java.time.ZoneOffset.UTC);
            assertThat(context.containsBean("entityManagerFactory")).isFalse();
            assertThat(context.containsBean("dataSource")).isFalse();
            assertThat(context.getEnvironment().getProperty("spring.application.name")).isEqualTo("device-comm");
            var redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);
            assertThat(redisTemplate.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
            assertThat(redisTemplate.getValueSerializer()).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
            assertThat(output).contains("TCP Server successfully bound");
        }
        assertThat(output).contains("TCP Server stopped");
    }
}
