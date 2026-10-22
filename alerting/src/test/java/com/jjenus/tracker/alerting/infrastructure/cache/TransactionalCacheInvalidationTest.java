package com.jjenus.tracker.alerting.infrastructure.cache;

import com.jjenus.tracker.alerting.support.RedisCacheTestStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionalCacheInvalidationTest {
    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void defer_noTransaction_runsExistingImmediatePath() {
        assertThat(TransactionalCacheInvalidation.defer(new RedisCacheTestStore().template(), "tenant:1:*")).isFalse();
    }

    @Test
    void defer_readOnlyTransaction_allowsCommittedReadCachePopulation() {
        // given
        startTransaction();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        // when / then
        assertThat(TransactionalCacheInvalidation.defer(new RedisCacheTestStore().template(), "tenant:1:*")).isFalse();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void defer_repeatedWrites_coalescesPatternsAndEvictsAfterCommitOnly() {
        // given
        var redis = new RedisCacheTestStore().template();
        redis.opsForValue().set("tenant:1:detail", "old", 1, TimeUnit.HOURS);
        redis.opsForValue().set("tenant:2:detail", "other", 1, TimeUnit.HOURS);
        startTransaction();

        // when
        assertThat(TransactionalCacheInvalidation.defer(redis, "tenant:1:*")).isTrue();
        assertThat(TransactionalCacheInvalidation.defer(redis, "tenant:1:*")).isTrue();
        var callbacks = TransactionSynchronizationManager.getSynchronizations();

        // then
        assertThat(callbacks).hasSize(1);
        assertThat(redis.opsForValue().get("tenant:1:detail")).isEqualTo("old");
        callbacks.forEach(TransactionSynchronization::afterCommit);
        assertThat(redis.opsForValue().get("tenant:1:detail")).isNull();
        assertThat(redis.opsForValue().get("tenant:2:detail")).isEqualTo("other");
    }

    @Test
    void defer_rollback_leavesExistingCacheUntouched() {
        // given
        var redis = new RedisCacheTestStore().template();
        redis.opsForValue().set("tenant:1:detail", "old", 1, TimeUnit.HOURS);
        startTransaction();
        TransactionalCacheInvalidation.defer(redis, "tenant:1:*");

        // when
        TransactionSynchronizationManager.getSynchronizations().forEach(
            callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        // then
        assertThat(redis.opsForValue().get("tenant:1:detail")).isEqualTo("old");
    }

    @Test
    @SuppressWarnings("unchecked")
    void afterCommit_redisUnavailable_doesNotMisreportDatabaseRollback() {
        // given
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class, invocation -> {
            throw new IllegalStateException("Redis unavailable");
        });
        startTransaction();
        TransactionalCacheInvalidation.defer(redis, "tenant:1:*");

        // when / then
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(
            TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }

    @Test
    void defer_transactionWithoutSynchronization_refusesUncommittedCacheWrite() {
        // given
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // when / then
        assertThatThrownBy(() -> TransactionalCacheInvalidation.defer(new RedisCacheTestStore().template(), "tenant:1:*"))
            .isInstanceOf(IllegalStateException.class);
    }

    private void startTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
