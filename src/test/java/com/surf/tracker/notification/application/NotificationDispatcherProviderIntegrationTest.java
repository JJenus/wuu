package com.surf.tracker.notification.application;

import com.surf.tracker.notification.application.service.EmailNotificationService;
import com.surf.tracker.notification.application.service.PushNotificationService;
import com.surf.tracker.notification.application.service.SmsNotificationService;
import com.surf.tracker.notification.application.service.WebSocketNotificationService;
import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.entity.DeliveryEvent;
import com.surf.tracker.notification.domain.enums.DeliveryEventType;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.ErrorType;
import com.surf.tracker.notification.domain.enums.NotificationChannel;
import com.surf.tracker.notification.infrastructure.repository.DeliveryEventRepository;
import com.surf.tracker.notification.infrastructure.repository.DeliveryRepository;
import com.surf.tracker.notification.infrastructure.repository.DeviceRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationDispatcherProviderIntegrationTest {

    @ParameterizedTest
    @EnumSource(value = NotificationChannel.class, names = {"SMS", "MOBILE_PUSH"})
    void dispatch_unsupported_provider_records_failure_not_sent(NotificationChannel channel) {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("delivery-provider-safety");
        delivery.setChannel(channel);
        DeliveryEventRepository eventRepository = mock(DeliveryEventRepository.class);
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(SmsNotificationService.class)
            .withBean(PushNotificationService.class)
            .withPropertyValues("notification.sms.enabled=true", "notification.sms.provider=twilio",
                "notification.push.enabled=true", "notification.push.provider=firebase");

        contextRunner.run(context -> {
            NotificationDispatcher dispatcher = new NotificationDispatcher(
                mock(WebSocketNotificationService.class), mock(EmailNotificationService.class),
                context.getBean(SmsNotificationService.class), context.getBean(PushNotificationService.class),
                mock(DeliveryRepository.class), eventRepository, mock(DeviceRepository.class),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

            // when
            dispatcher.dispatch(delivery);

            // then
            ArgumentCaptor<DeliveryEvent> event = ArgumentCaptor.forClass(DeliveryEvent.class);
            verify(eventRepository).save(event.capture());
            assertSoftly(softly -> {
                softly.assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
                softly.assertThat(delivery.getSentAt()).isNull();
                softly.assertThat(delivery.getDeliveredAt()).isNull();
                softly.assertThat(delivery.getLastErrorType()).isEqualTo(ErrorType.PERMANENT);
                softly.assertThat(delivery.getNextRetryAt()).isNull();
                softly.assertThat(delivery.canRetry()).isFalse();
                softly.assertThat(event.getValue().getEventType()).isEqualTo(DeliveryEventType.FAILED);
            });
        });
    }
}
