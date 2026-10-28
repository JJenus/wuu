package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.userauth.application.service.LoginAttemptService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({JwtConfig.class, LoginPolicyConfig.class})
public class AuthCoreConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
