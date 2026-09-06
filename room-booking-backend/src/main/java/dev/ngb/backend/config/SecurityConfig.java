package dev.ngb.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import lombok.RequiredArgsConstructor;

/**
 * Defines password encoding and the HTTP security rules for the stateless REST API.
 *
 * <p>{@code @Configuration} tells Spring that methods in this class declare managed objects.
 * Lombok's {@code @RequiredArgsConstructor} generates constructor injection for the three final
 * collaborators. Constructor injection makes dependencies mandatory and easy to replace in tests.</p>
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * Creates an encoder that stores an algorithm identifier alongside each password hash.
     *
     * <p>{@code @Bean} registers the returned object in Spring's application context, allowing
     * services to request the {@link PasswordEncoder} interface.</p>
     *
     * @return delegating password encoder using Spring Security's current default
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Builds the filter chain that separates public authentication endpoints from protected APIs.
     *
     * <p>CSRF, form login, and HTTP Basic are disabled because clients authenticate this JSON API
     * with bearer tokens. {@code STATELESS} prevents creation of server-side login sessions. The
     * JWT filter runs before Spring's username/password filter so authorization sees its principal.</p>
     *
     * @param http fluent Spring Security configuration supplied by the framework
     * @return immutable security filter chain used for incoming HTTP requests
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset",
                                "/api/v1/auth/email-verification/confirm")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/email-exists")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
