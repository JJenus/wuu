package com.surf.tracker.notification.application.service;

import com.surf.tracker.notification.domain.entity.Delivery;
import com.surf.tracker.notification.domain.enums.DeliveryStatus;
import com.surf.tracker.notification.domain.enums.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PushNotificationServiceTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(PushNotificationService.class);

    @ParameterizedTest
    @ValueSource(strings = {"firebase", "unknown", "none", ""})
    void send_unsupported_provider_returns_permanent_failure_without_sent_state(String provider) {
        // given
        Delivery delivery = new Delivery();

        contextRunner.withPropertyValues("notification.push.enabled=true",
            "notification.push.provider=" + provider).run(context -> {
                // when
                DeliveryResult result = context.getBean(PushNotificationService.class).send(delivery);

                // then
                assertThat(result.ok()).isFalse();
                assertThat(result.errorType()).isEqualTo(ErrorType.PERMANENT);
                assertThat(result.error()).contains("Unsupported push provider");
                assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
                assertThat(delivery.getSentAt()).isNull();
                assertThat(delivery.getDeliveredAt()).isNull();
                assertThat(delivery.getAttemptCount()).isZero();
            });
    }

    @Test
    void isavailable_configured_unsupported_provider_returns_false() {
        // given
        contextRunner.withPropertyValues("notification.push.enabled=true",
            "notification.push.provider=firebase").run(context -> {
                // when
                boolean available = context.getBean(PushNotificationService.class).isAvailable();

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
            PushNotificationService service = context.getBean(PushNotificationService.class);
            DeliveryResult result = service.send(delivery);

            // then
            assertThat(result).isEqualTo(DeliveryResult.failure("Push notifications are disabled", ErrorType.PERMANENT));
            assertThat(service.isAvailable()).isFalse();
            assertThat(service.getChannel()).isEqualTo("MOBILE_PUSH");
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(delivery.getSentAt()).isNull();
        });
    }
}
