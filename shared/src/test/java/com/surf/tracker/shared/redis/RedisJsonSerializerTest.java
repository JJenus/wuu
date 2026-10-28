package com.surf.tracker.shared.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisJsonSerializerTest {
    @Test
    void create_applicationMapper_doesNotChangeExternalJson() throws Exception {
        // given
        ObjectMapper mapper = new ObjectMapper();
        String before = mapper.writeValueAsString(Map.of("count", 2L));

        // when
        var serializer = RedisJsonSerializer.create(mapper);
        Object restored = serializer.deserialize(serializer.serialize(Map.of("count", 2L)));

        // then
        assertThat(restored).isEqualTo(Map.of("count", 2L));
        assertThat(mapper.writeValueAsString(Map.of("count", 2L))).isEqualTo(before);
    }

    @Test
    void deserialize_unapprovedClass_rejectsTypeId() {
        // given
        var serializer = RedisJsonSerializer.create(new ObjectMapper());
        byte[] payload = "{\"@class\":\"java.io.File\",\"path\":\"/tmp/ignored\"}".getBytes(StandardCharsets.UTF_8);

        // when / then
        assertThatThrownBy(() -> serializer.deserialize(payload)).isInstanceOf(SerializationException.class)
            .hasMessageContaining("denied resolution");
    }
}
