package com.surf.tracker.devicecomm;

import com.surf.tracker.devicecomm.application.DeviceDataProcessor;
import com.surf.tracker.devicecomm.application.ParserFactory;
import com.surf.tracker.devicecomm.infrastructure.AutoseekerProtocolParser;
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
    void run_standaloneConfiguration_startsDeviceInfrastructureWithoutBusinessModules(CapturedOutput output) throws Exception {
        // given
        var application = new SpringApplicationBuilder(DeviceCommApplication.class);

        // when
        try (var context = application.run("--tracking.tcp.server.port=0",
                "--spring.main.keep-alive=false", "--tracking.tcp.autoseeker.timestamp-zone=Asia/Singapore")) {
            // then
            assertThat(context.getBean(DeviceDataProcessor.class)).isNotNull();
            assertThat(context.getBean(ReactiveTcpServer.class)).isNotNull();
            String packet = "*HQ,8168000008,V1,043602,A,2234.9273,N,11354.3980,E,000.06,000,100715,FBFBBFF,460,00,10342,4283,10,25,128#";
            var configuredParser = context.getBean(AutoseekerProtocolParser.class);
            assertThat(context.getBean(ParserFactory.class).getParser(packet)).isSameAs(configuredParser);
            assertThat(configuredParser.parse(packet).timestamp()).isEqualTo(java.time.Instant.parse("2015-07-09T20:36:02Z"));
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
