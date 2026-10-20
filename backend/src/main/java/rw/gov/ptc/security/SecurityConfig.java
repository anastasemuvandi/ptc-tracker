package rw.gov.ptc.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import rw.gov.ptc.auth.api.TokenVerifier;
import rw.gov.ptc.idempotency.IdempotencyFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenVerifier verifier,
                                           JdbcTemplate jdbc) throws Exception {
        BearerTokenFilter bearer = new BearerTokenFilter(verifier);
        IdempotencyFilter idempotency = new IdempotencyFilter(jdbc);
        ReadOnlyGuardFilter readOnly = new ReadOnlyGuardFilter();
        http
                // Stateless bearer tokens; the refresh cookie is SameSite=Strict
                // and its endpoints require a custom header.
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/.well-known/jwks.json", "/api/auth/jwks", "/api/auth/login",
                                "/api/auth/mfa/verify", "/api/auth/mfa/enrol/**", "/api/auth/refresh",
                                "/api/auth/logout", "/api/auth/invitations/accept", "/api/auth/password/forgot",
                                "/api/auth/password/reset", "/api/public/**", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(bearer, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(readOnly, BearerTokenFilter.class)
                .addFilterAfter(idempotency, ReadOnlyGuardFilter.class);
        return http.build();
    }
}
