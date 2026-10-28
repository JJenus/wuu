package com.surf.tracker.notification.application.event;

import com.surf.tracker.notification.application.NotificationOrchestrator;
import com.surf.tracker.shared.events.AlertRaisedEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NotificationEventHandlerTest {
    @Test
    void handleAlertRaised_unresolvedRecipientPolicy_propagatesFailureForBrokerRecovery() {
        // given
        var orchestrator = mock(NotificationOrchestrator.class);
        var handler = new NotificationEventHandler(orchestrator);
        var event = new AlertRaisedEvent("source", Instant.EPOCH, "alert", "rule", "vehicle",
            "OVERSPEED", "WARNING", "fixture", Instant.EPOCH, null, null, null, Map.of());
        var failure = new IllegalStateException("Alert notification recipient policy is not configured");
        doThrow(failure).when(orchestrator).processAlert(event);
        // when / then
        assertThatThrownBy(() -> handler.handleAlertRaised(event)).isSameAs(failure);
    }
}
