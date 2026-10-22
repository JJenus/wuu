package com.jjenus.tracker.alerting.infrastructure.cache;

import com.jjenus.tracker.shared.redis.RedisKeyScanner;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.Set;

/** Do not publish mutable entities from write transactions. Evict captured tenant keys after commit. */
public final class TransactionalCacheInvalidation {
    private TransactionalCacheInvalidation() {}

    public static boolean defer(RedisTemplate<String, Object> redis, String pattern) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) return false;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Do not publish uncommitted state even with an unusual transaction manager.
            throw new IllegalStateException("Cache invalidation requires transaction synchronization");
        }
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            if (synchronization instanceof PendingInvalidation pending && pending.redis == redis) {
                pending.patterns.add(pattern);
                return true;
            }
        }
        PendingInvalidation pending = new PendingInvalidation(redis);
        pending.patterns.add(pattern);
        TransactionSynchronizationManager.registerSynchronization(pending);
        return true;
    }

    private static final class PendingInvalidation implements TransactionSynchronization {
        private final RedisTemplate<String, Object> redis;
        private final Set<String> patterns = new LinkedHashSet<>();

        private PendingInvalidation(RedisTemplate<String, Object> redis) {
            this.redis = redis;
        }

        @Override
        public void afterCommit() {
            for (String pattern : patterns) {
                try {
                    var keys = RedisKeyScanner.scanKeys(redis, pattern);
                    if (!keys.isEmpty()) redis.delete(keys);
                } catch (RuntimeException exception) {
                    // The DB is already committed; retain the manual caches' best-effort policy.
                    LoggerFactory.getLogger(TransactionalCacheInvalidation.class)
                        .error("Post-commit cache invalidation failed for {}", pattern, exception);
                }
            }
        }
    }
}
