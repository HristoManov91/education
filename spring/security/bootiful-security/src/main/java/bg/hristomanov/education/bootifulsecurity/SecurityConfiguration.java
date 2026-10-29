package bg.hristomanov.education.bootifulsecurity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationManagerFactories;
import org.springframework.security.authorization.AuthorizationManagerFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        // Prefixes encoded hashes with an algorithm id, allowing migration later.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        // Test-only credentials. Never reuse these users in deployed applications.
        return new InMemoryUserDetailsManager(
                User.withUsername("alice")
                        .password(encoder.encode("local-demo-password"))
                        .roles("USER")
                        .build(),
                User.withUsername("admin")
                        .password(encoder.encode("local-demo-password"))
                        .roles("USER", "ADMIN")
                        .build()
        );
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            OneTimeTokenGenerationSuccessHandler tokenDelivery)
            throws Exception {

        AuthorizationManagerFactory<Object> stepUpMfa = AuthorizationManagerFactories
                .<Object>multiFactor()
                .requireFactors(
                        FactorGrantedAuthority.PASSWORD_AUTHORITY,
                        FactorGrantedAuthority.OTT_AUTHORITY)
                .build();

        // Declaring our own chain replaces Boot's default chain: re-state every rule.
        // Keep CSRF enabled because this is a browser/session application.
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/public", "/ott/sent").permitAll()
                        .requestMatchers("/api/admin/**").access(stepUpMfa.hasRole("ADMIN"))
                        .requestMatchers("/api/me").authenticated()
                        .anyRequest().denyAll())
                .formLogin(Customizer.withDefaults())
                .oneTimeTokenLogin(Customizer.withDefaults())
                .build();
    }
}
