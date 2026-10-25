package com.surf.tracker.notification.application.queue;

import com.surf.tracker.notification.application.NotificationOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationQueueConsumerTest {

    @Mock
    private NotificationOrchestrator orchestrator;

    @Mock
    private NotificationQueuePublisher publisher;

    private NotificationQueueConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationQueueConsumer(orchestrator, publisher);
    }

    @Test
    void onMessage_validMessage_callsOrchestrator() {
        NotificationMessage message = new NotificationMessage(
            "user_1", "alert_1", "TPL_001", Map.of("key", "value")
        );

        consumer.onMessage(message);

        verify(orchestrator).processNotification(message);
        verify(publisher, never()).sendToDlq(any());
    }

    @Test
    void onMessage_orchestratorThrows_sendsToDlq() {
        NotificationMessage message = new NotificationMessage(
            "user_1", "alert_1", "TPL_001", Map.of()
        );

        doThrow(new RuntimeException("boom"))
            .when(orchestrator).processNotification(any(NotificationMessage.class));

        consumer.onMessage(message);

        verify(orchestrator).processNotification(message);
        verify(publisher).sendToDlq(message);
    }
}