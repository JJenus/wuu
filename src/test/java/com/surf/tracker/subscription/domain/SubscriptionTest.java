package com.surf.tracker.subscription.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static com.surf.tracker.subscription.domain.SubscriptionTestBuilder.*;

@Timeout(15)
class SubscriptionTest {
    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(PlanVersion.Cadence.class)
    void consume_periodic_configured_grant_is_bounded_for_each_cadence(PlanVersion.Cadence cadence) {
        // given
        var subscription = defaultSubscription().withPeriodic(cadence).activated();
        // when
        var receipt = subscription.consume("periodic", "location_updates", 10, clock(END.minusNanos(1)));
        // then
        assertThat(receipt.remainingUnits()).isZero();
        assertThat(subscription.plan().cadence()).isEqualTo(cadence);
        assertThat(subscription.status(clock(START))).isEqualTo(Subscription.Status.TRIALING);
    }

    @Test
    void activate_future_start_has_no_access_until_start() {
        // given
        var subscription = defaultSubscription().build();
        // when
        subscription.activate(grant(1), clock(START.minusSeconds(1)));
        // then
        assertThat(subscription.remaining(clock(START.minusNanos(1)))).isZero();
        assertThat(subscription.status(clock(START.minusNanos(1)))).isEqualTo(Subscription.Status.TRIALING);
        assertThatThrownBy(() -> subscription.consume("early", "location_updates", 1, clock(START.minusNanos(1))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(subscription.remaining(clock(START))).isEqualTo(1);
    }

    @Test
    void consume_failed_operation_does_not_reserve_key_or_expose_mutable_ledger() {
        // given
        var subscription = defaultSubscription().activated();
        var emptySnapshot = subscription.ledger();
        assertThatThrownBy(() -> subscription.consume("retry", "location_updates", 11, clock(START)))
                .isInstanceOf(IllegalArgumentException.class);
        // when
        var receipt = subscription.consume("retry", "location_updates", 1, clock(START));
        // then
        assertThat(receipt.remainingUnits()).isEqualTo(9);
        assertThat(emptySnapshot).isEmpty();
        assertThat(subscription.ledger()).containsExactly(receipt);
    }

    @Test
    void consume_pending_has_zero_entitlement() {
        // given
        var subscription = defaultSubscription().build();
        // when / then
        assertThat(subscription.remaining(clock(START))).isZero();
        assertThat(subscription.status(clock(START))).isEqualTo(Subscription.Status.PENDING);
        assertThatThrownBy(() -> subscription.consume("op", "location_updates", 1, clock(START))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void activate_explicit_grant_is_not_payment_success() {
        // given
        var subscription = defaultSubscription().withPeriodic(PlanVersion.Cadence.YEARLY).build();
        var grant = new AdminGrant(AdminGrant.Kind.COMPLIMENTARY, "admin", "configured access", START, END, 3);
        // when
        subscription.activate(grant, clock(START));
        // then
        assertThat(subscription.status(clock(START))).isEqualTo(Subscription.Status.ADMIN_GRANTED);
        assertThat(subscription.remaining(clock(START))).isEqualTo(3);
        assertThat(subscription.grant()).contains(grant);
    }

    @Test
    void activate_oversized_expired_or_duplicate_grant_rejects() {
        // given
        var pending = defaultSubscription().build();
        var active = defaultSubscription().activated();
        // when / then
        assertThatThrownBy(() -> pending.activate(grant(11), clock(START))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.activate(grant(1), clock(END))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> active.activate(grant(1), clock(START))).isInstanceOf(IllegalStateException.class);
        assertThat(pending.remaining(clock(START))).isZero();
    }

    @Test
    void consume_same_key_and_payload_returns_original_receipt_after_cancellation() {
        // given
        var subscription = defaultSubscription().activated();
        var receipt = subscription.consume("operation-1", "location_updates", 3, clock(START));
        subscription.cancel();
        // when
        var replay = subscription.consume("operation-1", "location_updates", 3, clock(END));
        // then
        assertThat(replay).isSameAs(receipt);
        assertThat(receipt.remainingUnits()).isEqualTo(7);
        assertThat(receipt.recordedAt()).isEqualTo(START);
        assertThat(receipt.subscriptionId()).isEqualTo(ID);
        assertThat(subscription.ledger()).containsExactly(receipt);
        assertThatThrownBy(() -> subscription.ledger().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void consume_same_key_different_payload_conflicts_without_debit() {
        // given
        var subscription = defaultSubscription().activated();
        subscription.consume("operation-1", "location_updates", 3, clock(START));
        // when / then
        assertThatThrownBy(() -> subscription.consume("operation-1", "location_updates", 2, clock(START))).isInstanceOf(Subscription.IdempotencyConflict.class);
        assertThatThrownBy(() -> subscription.consume("operation-1", "another_meter", 3, clock(START))).isInstanceOf(Subscription.IdempotencyConflict.class);
        assertThat(subscription.remaining(clock(START))).isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE, 11, Long.MAX_VALUE})
    void consume_invalid_or_excess_units_does_not_mutate(long units) {
        // given
        var subscription = defaultSubscription().activated();
        // when / then
        assertThatThrownBy(() -> subscription.consume("invalid", "location_updates", units, clock(START))).isInstanceOf(IllegalArgumentException.class);
        assertThat(subscription.remaining(clock(START))).isEqualTo(10);
        assertThat(subscription.ledger()).isEmpty();
    }

    @Test
    void consume_wrong_meter_or_blank_key_does_not_mutate() {
        // given
        var subscription = defaultSubscription().activated();
        // when / then
        assertThatThrownBy(() -> subscription.consume("op", "wrong", 1, clock(START))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> subscription.consume(" ", "location_updates", 1, clock(START))).isInstanceOf(IllegalArgumentException.class);
        assertThat(subscription.ledger()).isEmpty();
    }

    @Test
    void consume_maximum_allowance_never_overflows() {
        // given
        var subscription = defaultSubscription().withLimit(Long.MAX_VALUE).activated();
        subscription.consume("first", "location_updates", Long.MAX_VALUE - 1, clock(START));
        // when
        var last = subscription.consume("last", "location_updates", 1, clock(START));
        // then
        assertThat(last.remainingUnits()).isZero();
        assertThatThrownBy(() -> subscription.consume("overflow", "location_updates", Long.MAX_VALUE, clock(START))).isInstanceOf(IllegalArgumentException.class);
        assertThat(subscription.remaining(clock(START))).isZero();
    }

    @Test
    void consume_before_start_or_at_end_denies_and_expiry_is_terminal() {
        // given
        var subscription = defaultSubscription().activated();
        // when / then
        assertThat(subscription.remaining(clock(START.minusNanos(1)))).isZero();
        assertThatThrownBy(() -> subscription.consume("early", "location_updates", 1, clock(START.minusNanos(1)))).isInstanceOf(IllegalStateException.class);
        assertThat(subscription.remaining(clock(END))).isZero();
        assertThat(subscription.status(clock(END))).isEqualTo(Subscription.Status.CANCELED);
        assertThatThrownBy(() -> subscription.consume("late", "location_updates", 1, clock(END))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> subscription.activate(grant(1), clock(START))).isInstanceOf(IllegalStateException.class);
        assertThat(subscription.remaining(clock(START))).isZero();
    }

    @Test
    void cancel_pending_or_active_is_terminal_and_idempotent() {
        // given
        var subscription = defaultSubscription().build();
        // when
        subscription.cancel();
        subscription.cancel();
        // then
        assertThat(subscription.status(clock(START))).isEqualTo(Subscription.Status.CANCELED);
        assertThatThrownBy(() -> subscription.activate(grant(1), clock(START))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> subscription.consume("op", "location_updates", 1, clock(START))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consume_concurrent_distinct_keys_never_exceeds_allowance() throws Exception {
        // given
        var subscription = defaultSubscription().withLimit(1).activated();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8, Thread.ofPlatform().daemon().factory());
        try {
            var tasks = IntStream.range(0, 8).mapToObj(index -> (Callable<Boolean>) () -> {
                start.await();
                try {
                    subscription.consume("op-" + index, "location_updates", 1, clock(START));
                    return true;
                } catch (IllegalArgumentException exhausted) {
                    return false;
                }
            }).map(executor::submit).toList();
            // when
            start.countDown();
            int successes = 0;
            for (var task : tasks) {
                if (task.get(5, TimeUnit.SECONDS)) successes++;
            }
            // then
            assertThat(successes).isEqualTo(1);
            assertThat(subscription.ledger()).hasSize(1);
            assertThat(subscription.remaining(clock(START))).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void consume_concurrent_same_key_debits_once() throws Exception {
        // given
        var subscription = defaultSubscription().activated();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8, Thread.ofPlatform().daemon().factory());
        try {
            var tasks = IntStream.range(0, 8).mapToObj(index -> executor.submit(() -> {
                start.await();
                return subscription.consume("shared", "location_updates", 1, clock(START));
            })).toList();
            // when
            start.countDown();
            var original = tasks.getFirst().get(5, TimeUnit.SECONDS);
            // then
            for (var task : tasks) assertThat(task.get(5, TimeUnit.SECONDS)).isSameAs(original);
            assertThat(subscription.ledger()).hasSize(1);
            assertThat(subscription.remaining(clock(START))).isEqualTo(9);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void consume_replay_after_unobserved_expiry_returns_receipt_without_new_access() {
        // given
        var subscription = defaultSubscription().activated();
        var original = subscription.consume("once", "location_updates", 1, clock(START));
        // when / then: no status/remaining call has observed expiry before the replay.
        assertThat(subscription.consume("once", "location_updates", 1, clock(END))).isSameAs(original);
        assertThat(subscription.ledger()).containsExactly(original);
        assertThatThrownBy(() -> subscription.consume("new", "location_updates", 1, clock(END)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(subscription.remaining(clock(END))).isZero();
    }

    @Test
    void consume_concurrent_same_key_different_payload_has_one_winner() throws Exception {
        // given
        var subscription = defaultSubscription().activated();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            var tasks = IntStream.rangeClosed(1, 2).mapToObj(units -> executor.submit(() -> {
                start.await();
                try {
                    subscription.consume("shared", "location_updates", units, clock(START));
                    return true;
                } catch (Subscription.IdempotencyConflict conflict) {
                    return false;
                }
            })).toList();
            // when
            start.countDown();
            int successes = 0;
            for (var task : tasks) if (task.get(5, TimeUnit.SECONDS)) successes++;
            // then
            assertThat(successes).isEqualTo(1);
            assertThat(subscription.ledger()).hasSize(1);
            assertThat(subscription.remaining(clock(START))).isEqualTo(10 - subscription.ledger().getFirst().units());
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancel_racing_activation_or_consumption_is_terminal(boolean alreadyActivated) throws Exception {
        // given
        var subscription = alreadyActivated ? defaultSubscription().activated() : defaultSubscription().build();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
        try {
            var mutation = executor.submit(() -> {
                start.await();
                try {
                    if (alreadyActivated) subscription.consume("race", "location_updates", 1, clock(START));
                    else subscription.activate(grant(1), clock(START));
                    return true;
                } catch (IllegalStateException canceled) {
                    return false;
                }
            });
            var cancellation = executor.submit(() -> {
                start.await();
                subscription.cancel();
                return true;
            });
            // when
            start.countDown();
            boolean mutationSucceeded = mutation.get(5, TimeUnit.SECONDS);
            assertThat(cancellation.get(5, TimeUnit.SECONDS)).isTrue();
            // then
            assertThat(subscription.status(clock(START))).isEqualTo(Subscription.Status.CANCELED);
            assertThat(subscription.remaining(clock(START))).isZero();
            assertThat(subscription.ledger()).hasSize(alreadyActivated && mutationSucceeded ? 1 : 0);
            assertThatThrownBy(() -> subscription.activate(grant(1), clock(START))).isInstanceOf(IllegalStateException.class);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        }
    }
}
