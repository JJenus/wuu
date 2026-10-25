package com.surf.tracker.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = "com.surf.tracker.**",
        exclude = {}
)
@EnableScheduling
@EntityScan("com.surf.tracker.**")
@EnableJpaRepositories("com.surf.tracker.**")
@EnableRedisRepositories("com.surf.tracker.**")
public class TrackingEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrackingEngineApplication.class, args);
    }
}