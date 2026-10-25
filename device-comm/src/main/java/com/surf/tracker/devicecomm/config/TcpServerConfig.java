//package com.surf.tracker.devicecomm.config;
//
//import com.surf.tracker.devicecomm.service.ReactiveTcpServer;
//import jakarta.annotation.PreDestroy;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.boot.context.event.ApplicationReadyEvent;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.event.EventListener;
//
//@Configuration
//public class TcpServerConfig {
//    private static final Logger logger = LoggerFactory.getLogger(TcpServerConfig.class);
//
//    @Bean
//    public ReactiveTcpServer reactiveTcpServer() {
//        return new ReactiveTcpServer(deviceDataProcessor);
//    }
//
//    @EventListener(ApplicationReadyEvent.class)
//    public void startTcpServerOnStartup(ApplicationReadyEvent event) {
//        // Delay startup to ensure all beans are ready
//        ReactiveTcpServer server = event.getApplicationContext()
//                .getBean(ReactiveTcpServer.class);
//
//        // Start in a separate thread to not block main thread
//        new Thread(() -> {
//            try {
//                Thread.sleep(3000); // Wait for Spring to fully initialize
//                server.start();
//            } catch (Exception e) {
//                logger.error("Failed to start TCP server", e);
//                // Don't crash the app, just log error
//            }
//        }, "tcp-server-starter").start();
//    }
//
//    @PreDestroy
//    public void cleanup() {
//        ReactiveTcpServer server = applicationContext.getBean(ReactiveTcpServer.class);
//        server.stop();
//    }
//}