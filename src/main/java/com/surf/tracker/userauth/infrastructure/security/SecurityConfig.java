package com.surf.tracker.userauth.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;

import java.util.List;

import static com.surf.tracker.main.api.ApiVersionConfig.V1;

@Configuration
@EnableMethodSecurity
@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AuthRateLimitFilter rateLimitFilter;
    @Value("${userauth.cors.allowed-origins:}")
    private List<String> allowedOrigins = List.of();
    @Value("${userauth.registration-enabled:false}")
    private boolean registrationEnabled;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, AuthRateLimitFilter rateLimitFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, V1 + "/auth/login", V1 + "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, V1 + "/auth/register").access((authn, request) -> new AuthorizationDecision(registrationEnabled))
                .requestMatchers(HttpMethod.POST, V1 + "/auth/logout", V1 + "/auth/change-password").authenticated()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/h2-console/**", V1 + "/notifications/webhooks/**",
                    V1 + "/commands/*/send", V1 + "/commands/*/deliver", V1 + "/commands/*/fail").denyAll()
                .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**").hasAuthority("system.admin")
                .requestMatchers(HttpMethod.GET, V1 + "/users/me", V1 + "/users/me/access").authenticated()
                .requestMatchers(HttpMethod.GET, V1 + "/roles").hasAuthority("roles.read")
                .requestMatchers(V1 + "/users/*/roles").hasAuthority("users.assign_roles")
                .requestMatchers(HttpMethod.GET, V1 + "/users/**").hasAuthority("users.read")
                .requestMatchers(HttpMethod.DELETE, V1 + "/users/**").hasAuthority("users.delete")
                .requestMatchers(V1 + "/users/**").hasAuthority("users.write")
                .requestMatchers(V1 + "/organizations/**").authenticated()
                .requestMatchers(HttpMethod.POST, V1 + "/subscriptions/catalog").hasAuthority("system.admin")
                .requestMatchers(HttpMethod.POST, V1 + "/subscriptions/*/grants",
                    V1 + "/subscriptions/*/consumptions").hasAuthority("system.admin")
                .requestMatchers(HttpMethod.GET, V1 + "/subscriptions/catalog/**").authenticated()
                .requestMatchers(V1 + "/subscriptions/**").authenticated()
                .requestMatchers(V1 + "/notifications/templates/**").hasAuthority("system.admin")
                .requestMatchers(V1 + "/notifications/**", "/ws/notifications/**").authenticated()
                .requestMatchers(HttpMethod.GET, V1 + "/trips/export").hasAuthority("trips.export")
                .requestMatchers(HttpMethod.GET, V1 + "/trips/**").hasAuthority("trips.read")
                .requestMatchers(HttpMethod.GET, V1 + "/vehicles/**").hasAuthority("vehicles.read")
                .requestMatchers(V1 + "/vehicles/**").hasAuthority("vehicles.write")
                .requestMatchers(HttpMethod.GET, V1 + "/trackers/**", V1 + "/commands/**").hasAuthority("devices.read")
                .requestMatchers(V1 + "/trackers/**").hasAuthority("vehicles.assign_device")
                .requestMatchers(HttpMethod.POST, V1 + "/commands").hasAuthority("devices.command")
                .requestMatchers(V1 + "/alerts/cleanup", V1 + "/alerts/automated").denyAll()
                .requestMatchers(HttpMethod.GET, V1 + "/alerts/**").hasAuthority("alerts.read")
                .requestMatchers(V1 + "/alerts/**").hasAuthority("alerts.write")
                .anyRequest().denyAll()
            )
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"UNAUTHENTICATED\"}");
                })
                .accessDeniedHandler((request, response, error) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"FORBIDDEN\"}");
                }))
            .headers(headers -> headers.frameOptions(frame -> frame.deny()))
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS origins must be explicit, not wildcard patterns");
        }
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        cfg.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    // The filters belong exclusively to the security chain, never the servlet container chain.
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthFilter> jwtRegistration() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(jwtAuthFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<AuthRateLimitFilter> rateLimitRegistration() {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(rateLimitFilter);
        registration.setEnabled(false);
        return registration;
    }
}
