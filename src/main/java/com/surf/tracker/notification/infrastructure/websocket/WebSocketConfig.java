package com.surf.tracker.notification.infrastructure.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    private final VehicleTrackingWebSocketHandler webSocketHandler;
    @org.springframework.beans.factory.annotation.Value("${userauth.cors.allowed-origins:}")
    private String[] allowedOrigins = new String[0];
    
    public WebSocketConfig(VehicleTrackingWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(webSocketHandler, "/ws/notifications")
                .setAllowedOrigins(java.util.Arrays.stream(allowedOrigins).filter(origin -> !origin.isBlank()).toArray(String[]::new))
                .withSockJS();
    }
}
