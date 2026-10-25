package com.surf.tracker.userauth.infrastructure.security;

import com.surf.tracker.userauth.application.service.LocalAccountSeeder;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "userauth.local-accounts.enabled", havingValue = "true")
public class LocalAccountsConfiguration {
    @Bean
    ApplicationRunner seedLocalAccounts(LocalAccountSeeder seeder) {
        return args -> seeder.seed();
    }
}
