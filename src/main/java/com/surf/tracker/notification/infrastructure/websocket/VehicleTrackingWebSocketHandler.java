package com.surf.tracker.notification.infrastructure.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.surf.tracker.userauth.infrastructure.security.AuthIdentityService;
import com.surf.tracker.userauth.infrastructure.security.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class VehicleTrackingWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final AuthIdentityService identities;

    public VehicleTrackingWebSocketHandler(ObjectMapper mapper, AuthIdentityService identities) {
        this.mapper = mapper;
        this.identities = identities;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        AuthPrincipal principal = principal(session);
        if (principal == null || principal.orgId() == null || !identities.isActive(principal)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.put(session.getId(), session);
        send(session, Map.of("type", "CONNECTED", "userId", principal.getName()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        AuthPrincipal principal = principal(session);
        if (principal == null || !identities.isActive(principal)) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        // Client-supplied user IDs and arbitrary topic subscriptions are never accepted.
        try {
            var body = mapper.readTree(message.getPayload());
            if ("PING".equals(body.path("type").asText())) {
                send(session, Map.of("type", "PONG", "userId", principal.getName()));
            } else {
                send(session, Map.of("type", "ERROR", "message", "Unsupported message type"));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            send(session, Map.of("type", "ERROR", "message", "Invalid message format"));
        }
    }

    public boolean sendAlertNotification(String userId, String tenantId, Object notification) throws IOException {
        boolean sent = false;
        for (WebSocketSession session : sessions.values()) {
            AuthPrincipal principal = principal(session);
            if (principal == null || !identities.isActive(principal)) {
                sessions.remove(session.getId());
                session.close(CloseStatus.POLICY_VIOLATION);
            } else if (principal.getName().equals(userId) && principal.orgId() != null
                    && principal.orgId().toString().equals(tenantId) && session.isOpen()) {
                send(session, notification);
                sent = true;
            }
        }
        return sent;
    }

    public boolean isUserConnected(String userId) {
        return sessions.values().stream().anyMatch(session -> session.isOpen() && principal(session) != null
            && principal(session).getName().equals(userId));
    }

    public int getConnectedUsersCount() { return sessions.size(); }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) throws Exception {
        sessions.remove(session.getId());
        session.close(CloseStatus.SERVER_ERROR);
    }

    private AuthPrincipal principal(WebSocketSession session) {
        if (session.getPrincipal() instanceof Authentication authentication && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthPrincipal principal) return principal;
        return null;
    }

    private void send(WebSocketSession session, Object message) throws IOException {
        synchronized (session) {
            if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
        }
    }
}
