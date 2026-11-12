package bg.hristomanov.education.threatmodel;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Първото trust boundary е authentication, но URL-level authZ не заменя
 * ownership проверката на конкретния бизнес запис в RecordAccessPolicy.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Оставяме CSRF включен: HTTP Basic може да е автоматично изпращан от браузър.
                .authorizeHttpRequests(auth -> auth
                        // Запазваме първоначалния статус при Spring MVC error dispatch.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/records/*/approve").hasRole("REVIEWER")
                        .requestMatchers(HttpMethod.GET, "/api/records/*").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
