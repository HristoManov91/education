package bg.hristomanov.education.aitesting.security;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless HTTP Basic demonstration only.
 * Disabling CSRF is NOT appropriate for cookie/session-based web applications.
 * No production test credentials are defined here.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain staffOnly(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Validation errors can be dispatched to /error by the
                        // servlet container. Do not turn HTTP 400 into 403.
                        // The original REQUEST still requires STAFF.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/customers/**").hasRole("STAFF")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
