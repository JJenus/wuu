package com.surf.tracker.devicecomm.service;

import com.surf.tracker.shared.pubsub.DeviceCommandTransport;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactiveTcpCommandTransport implements DeviceCommandTransport {
    private final ReactiveTcpServer tcpServer;

    public ReactiveTcpCommandTransport(ReactiveTcpServer tcpServer) {
        this.tcpServer = tcpServer;
    }

    @Override
    public Mono<Boolean> sendCommand(String deviceId, String command) {
        return tcpServer.sendCommandToDevice(deviceId, command);
    }
}
