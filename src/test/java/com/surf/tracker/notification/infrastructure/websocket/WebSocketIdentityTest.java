package com.surf.tracker.notification.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import java.net.URI;
import java.util.Map;
import static org.mockito.Mockito.*;

class WebSocketIdentityTest {
    @Test void connect_queryIdentityWithoutPrincipal_isClosed() throws Exception {
        // given
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("https://localhost/ws/notifications?userId=1"));
        when(session.getAttributes()).thenReturn(Map.of());
        var handler = new VehicleTrackingWebSocketHandler(new ObjectMapper(),
            mock(com.surf.tracker.userauth.infrastructure.security.AuthIdentityService.class));
        // when
        handler.afterConnectionEstablished(session);
        // then
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }
}
