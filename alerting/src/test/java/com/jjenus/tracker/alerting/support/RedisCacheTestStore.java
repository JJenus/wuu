package com.jjenus.tracker.alerting.support;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** In-memory Redis operations for cache flow tests; no TTL or wire-serialization simulation. */
public class RedisCacheTestStore {
    private final RedisTemplate<String, Object> template;
    private final Map<String, Object> entries = new HashMap<>();

    @SuppressWarnings("unchecked")
    public RedisCacheTestStore() {
        template = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        ListOperations<String, Object> lists = mock(ListOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(template.opsForList()).thenReturn(lists);
        when(values.get(anyString())).thenAnswer(invocation -> entries.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            entries.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), any(), anyLong(), any(TimeUnit.class));
        when(lists.range(anyString(), eq(0L), eq(-1L)))
            .thenAnswer(invocation -> entries.get(invocation.getArgument(0)));
        when(lists.rightPushAll(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            entries.put(invocation.getArgument(0), new ArrayList<>(
                List.of((Object[]) invocation.getRawArguments()[1])));
            return 1L;
        });
        when(template.delete(anyString())).thenAnswer(invocation -> entries.remove(invocation.getArgument(0)) != null);
        when(template.delete(anyCollection())).thenAnswer(invocation -> {
            ((Collection<String>) invocation.getArgument(0)).forEach(entries::remove);
            return 1L;
        });
        when(template.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            String pattern = ((ScanOptions) invocation.getArgument(0)).getPattern();
            var keys = entries.keySet().stream()
                .filter(key -> pattern != null && key.startsWith(pattern.replace("*", "")))
                .toList().iterator();
            Cursor<String> cursor = mock(Cursor.class);
            when(cursor.hasNext()).thenAnswer(ignored -> keys.hasNext());
            when(cursor.next()).thenAnswer(ignored -> keys.next());
            return cursor;
        });
    }

    public RedisTemplate<String, Object> template() {
        return template;
    }
}
