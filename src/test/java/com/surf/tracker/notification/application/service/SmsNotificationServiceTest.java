package com.surf.tracker.notification.application.service;

import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SmsNotificationServiceTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(SmsNotificationService.class);

    @ParameterizedTest
    @ValueSource(strings = {"twilio", "unknown", "none", ""})
    void send_unsupported_provider_returns_permanent_failure_without_sent_state(String provider) {
        // given
        Delivery delivery = new Delivery();

        contextRunner.withPropertyValues("notification.sms.enabled=true",
            "notification.sms.provider=" + provider).run(context -> {
                // when
                DeliveryResult result = context.getBean(SmsNotificationService.class).send(delivery);

                // then
                assertThat(result.ok()).isFalse();
                assertThat(result.errorType()).isEqualTo(ErrorType.PERMANENT);
                assertThat(result.error()).contains("Unsupported SMS provider");
                assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
                assertThat(delivery.getSentAt()).isNull();
                assertThat(delivery.getDeliveredAt()).isNull();
                assertThat(delivery.getAttemptCount()).isZero();
            });
    }

    @Test
    void isavailable_configured_unsupported_provider_returns_false() {
        // given
        contextRunner.withPropertyValues("notification.sms.enabled=true",
            "notification.sms.provider=twilio").run(context -> {
                // when
                boolean available = context.getBean(SmsNotificationService.class).isAvailable();

                // then
                assertThat(available).isFalse();
            });
    }

    @Test
    void send_disabled_returns_permanent_failure_without_sent_state() {
        // given
        Delivery delivery = new Delivery();

        contextRunner.run(context -> {
            // when
            SmsNotificationService service = context.getBean(SmsNotificationService.class);
            DeliveryResult result = service.send(delivery);

            // then
            assertThat(result).isEqualTo(DeliveryResult.failure("SMS notifications are disabled", ErrorType.PERMANENT));
            assertThat(service.isAvailable()).isFalse();
            assertThat(service.getChannel()).isEqualTo("SMS");
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(delivery.getSentAt()).isNull();
        });
    }
}
