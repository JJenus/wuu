package com.surf.tracker.notification.application.scheduler;

import com.surf.tracker.notification.application.NotificationDispatcher;
import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.ErrorType;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.infrastructure.repository.DeliveryEventRepository;
import com.surf.tracker.notification.infrastructure.repository.DeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryRetrySchedulerTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private DeliveryEventRepository eventRepository;

    @Mock
    private NotificationDispatcher dispatcher;

    private DeliveryRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), java.time.ZoneOffset.UTC);
        scheduler = new DeliveryRetryScheduler(deliveryRepository, eventRepository, dispatcher, fixed);
    }

    @Test
    void processRetries_noDeliveries_doesNothing() {
        when(deliveryRepository.findByStatusAndNextRetryAtLessThanEqual(
            eq(DeliveryStatus.FAILED), any(Instant.class)))
            .thenReturn(List.of());

        scheduler.processRetries();

        verify(dispatcher, never()).redispatch(any());
    }

    @Test
    void processRetries_retryableDelivery_redispatches() {
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("d_001");
        delivery.setChannel(NotificationChannel.EMAIL);
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setLastError("SMTP error");
        delivery.setLastErrorType(ErrorType.TRANSIENT);
        delivery.setAttemptCount(1);
        delivery.setMaxAttempts(5);

        when(deliveryRepository.findByStatusAndNextRetryAtLessThanEqual(
            eq(DeliveryStatus.FAILED), any(Instant.class)))
            .thenReturn(List.of(delivery));

        scheduler.processRetries();

        verify(dispatcher, times(1)).redispatch(delivery);
    }

    @Test
    void processRetries_nonRetryableDelivery_skipped() {
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("d_002");
        delivery.setChannel(NotificationChannel.EMAIL);
        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setLastError("bad token");
        delivery.setLastErrorType(ErrorType.PERMANENT);
        delivery.setAttemptCount(5);
        delivery.setMaxAttempts(5);

        when(deliveryRepository.findByStatusAndNextRetryAtLessThanEqual(
            eq(DeliveryStatus.FAILED), any(Instant.class)))
            .thenReturn(List.of(delivery));

        scheduler.processRetries();

        verify(dispatcher, never()).redispatch(any());
    }

    @Test
    void processRetries_multipleDeliveries_dispatchesEach() {
        Delivery d1 = new Delivery();
        d1.setDeliveryId("d_001");
        d1.setChannel(NotificationChannel.EMAIL);
        d1.setStatus(DeliveryStatus.FAILED);
        d1.setLastErrorType(ErrorType.TRANSIENT);
        d1.setAttemptCount(1);
        d1.setMaxAttempts(5);

        Delivery d2 = new Delivery();
        d2.setDeliveryId("d_002");
        d2.setChannel(NotificationChannel.SMS);
        d2.setStatus(DeliveryStatus.FAILED);
        d2.setLastErrorType(ErrorType.TRANSIENT);
        d2.setAttemptCount(2);
        d2.setMaxAttempts(5);

        when(deliveryRepository.findByStatusAndNextRetryAtLessThanEqual(
            eq(DeliveryStatus.FAILED), any(Instant.class)))
            .thenReturn(List.of(d1, d2));

        scheduler.processRetries();

        verify(dispatcher, times(1)).redispatch(d1);
        verify(dispatcher, times(1)).redispatch(d2);
    }

    @Test
    void processRetries_dispatcherThrows_logsAndContinues() {
        Delivery d1 = new Delivery();
        d1.setDeliveryId("d_001");
        d1.setChannel(NotificationChannel.EMAIL);
        d1.setStatus(DeliveryStatus.FAILED);
        d1.setLastErrorType(ErrorType.TRANSIENT);
        d1.setAttemptCount(1);
        d1.setMaxAttempts(5);

        when(deliveryRepository.findByStatusAndNextRetryAtLessThanEqual(
            eq(DeliveryStatus.FAILED), any(Instant.class)))
            .thenReturn(List.of(d1));
        doThrow(new RuntimeException("boom")).when(dispatcher).redispatch(d1);

        scheduler.processRetries();

        verify(dispatcher, times(1)).redispatch(d1);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}