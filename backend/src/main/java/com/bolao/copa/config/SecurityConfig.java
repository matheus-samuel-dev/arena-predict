package com.bolao.copa.config;

import com.bolao.copa.security.JwtAuthenticationFilter;
import com.bolao.copa.security.DemoAccessPolicy;
import com.bolao.copa.security.RestAccessDeniedHandler;
import com.bolao.copa.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(DemoProperties.class)
public class SecurityConfig {
    @Bean
    DemoAccessPolicy demoAccessPolicy(DemoProperties properties) { return new DemoAccessPolicy(properties); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            RestAuthenticationEntryPoint authenticationEntryPoint,
                                            RestAccessDeniedHandler accessDeniedHandler,
                                            DemoAccessPolicy demoAccessPolicy) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/auth/login", "/auth/register",
                                "/api/auth/login", "/api/auth/register",
                                "/api/auth/demo").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health", "/actuator/health/**", "/api/version",
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // The former PIX/Mercado Pago surface is intentionally retired.
                        // ArenaPredict only exposes the virtual-points wallet under /api/wallet.
                        .requestMatchers("/payments/**", "/api/payments/**").denyAll()
                        // Original Copa endpoints remain in the codebase only as a migration
                        // boundary. The supported product API is namespaced under /api.
                        .requestMatchers(
                                "/pools/**", "/matches/**", "/predictions/**",
                                "/ranking/**", "/dashboard/**").denyAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().access((authentication, context) -> new AuthorizationDecision(
                                demoAccessPolicy.allowsHttpRequest(authentication.get(), context.getRequest().getMethod(),
                                        context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length())))))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
