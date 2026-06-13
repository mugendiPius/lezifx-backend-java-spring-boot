package com.lezifx.trading.config;

import com.lezifx.trading.web.filter.ApiKeyResolutionFilter;
import com.lezifx.trading.web.filter.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiKeyResolutionFilter apiKeyResolutionFilter;
    private final JwtAuthFilter jwtAuthFilter;
    private final DynamicCorsConfigurationSource dynamicCors;

    public SecurityConfig(ApiKeyResolutionFilter apiKeyResolutionFilter,
                          JwtAuthFilter jwtAuthFilter,
                          DynamicCorsConfigurationSource dynamicCors) {
        this.apiKeyResolutionFilter = apiKeyResolutionFilter;
        this.jwtAuthFilter = jwtAuthFilter;
        this.dynamicCors = dynamicCors;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(dynamicCors))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/public/**",
                    "/api/v1/mpesa/**",
                    "/actuator/health",
                    "/actuator/info",
                    "/ws/**"
                ).permitAll()
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/register",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/api/v1/superadmin/auth/login",
                    "/api/v1/superadmin/auth/refresh"
                ).permitAll()
                .requestMatchers("/api/v1/superadmin/**")
                    .hasRole("SUPER_ADMIN")
                .requestMatchers("/api/v1/admin/**")
                    .hasAnyRole("ADMIN", "SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyResolutionFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}