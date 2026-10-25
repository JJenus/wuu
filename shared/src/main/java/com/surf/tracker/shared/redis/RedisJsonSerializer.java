package com.surf.tracker.shared.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonObjectWriter;

import java.util.regex.Pattern;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Redis-only typing: never changes the mapper used by HTTP or messaging. */
public final class RedisJsonSerializer {
    private RedisJsonSerializer() {}

    public static GenericJackson2JsonRedisSerializer create(ObjectMapper applicationMapper) {
        var validator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.surf.tracker.")
            .allowIfSubType(Pattern.compile("java\\.util\\.(ArrayList|LinkedList|HashMap|LinkedHashMap|HashSet|LinkedHashSet|TreeMap|TreeSet|"
                + "Collections\\$(Unmodifiable.*|Empty.*|Singleton.*)|ImmutableCollections\\$.*)"))
            .allowIfSubType(Pattern.compile("java\\.lang\\.(Long|Integer|Short|Byte|Double|Float|Boolean|String)"))
            .allowIfSubType("java.time.")
            .allowIfSubType("java.math.")
            .build();
        ObjectMapper redisMapper = applicationMapper.copy();
        // Immutable Map.of/List.of implementations are final and still need root type IDs.
        var typing = new ObjectMapper.DefaultTypeResolverBuilder(ObjectMapper.DefaultTyping.EVERYTHING, validator) {
            @Override
            protected TypeIdResolver idResolver(MapperConfig<?> config, JavaType baseType,
                    PolymorphicTypeValidator subtypeValidator, Collection<NamedType> subtypes,
                    boolean forSerialization, boolean forDeserialization) {
                TypeIdResolver delegate = super.idResolver(config, baseType, subtypeValidator,
                    subtypes, forSerialization, forDeserialization);
                if (!forSerialization) return delegate;
                return new ContainerTypeIds(delegate);
            }
        };
        typing.init(JsonTypeInfo.Id.CLASS, null).inclusion(JsonTypeInfo.As.PROPERTY);
        redisMapper.setDefaultTyping(typing);
        // Read as Object so the validator checks the root type as well as nested types.
        return new GenericJackson2JsonRedisSerializer(redisMapper,
            (mapper, source, type) -> mapper.readValue(source, Object.class), JacksonObjectWriter.create());
    }

    /** Store collection values, not ORM/immutable-wrapper implementation types. */
    private static final class ContainerTypeIds implements TypeIdResolver {
        private final TypeIdResolver delegate;

        private ContainerTypeIds(TypeIdResolver delegate) { this.delegate = delegate; }

        @Override
        public void init(JavaType baseType) { delegate.init(baseType); }

        @Override
        public String idFromValue(Object value) {
            String container = containerType(value);
            return container != null ? container : delegate.idFromValue(value);
        }

        @Override
        public String idFromValueAndType(Object value, Class<?> type) {
            String container = containerType(value);
            return container != null ? container : delegate.idFromValueAndType(value, type);
        }

        private String containerType(Object value) {
            if (value instanceof Map<?, ?>) return "java.util.LinkedHashMap";
            if (value instanceof Set<?>) return "java.util.LinkedHashSet";
            if (value instanceof List<?>) return "java.util.ArrayList";
            return null;
        }

        @Override
        public String idFromBaseType() { return delegate.idFromBaseType(); }

        @Override
        public JavaType typeFromId(DatabindContext context, String id) throws IOException {
            return delegate.typeFromId(context, id);
        }

        @Override
        public String getDescForKnownTypeIds() { return delegate.getDescForKnownTypeIds(); }

        @Override
        public JsonTypeInfo.Id getMechanism() { return delegate.getMechanism(); }
    }
}
