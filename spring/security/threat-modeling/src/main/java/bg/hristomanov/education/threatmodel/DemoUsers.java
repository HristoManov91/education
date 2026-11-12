package bg.hristomanov.education.threatmodel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Единствено за локални HTTP упражнения! Няма hardcoded credentials
 * в production профила; не активирай local-demo в реална среда.
 */
@Profile("local-demo")
@Configuration(proxyBeanMethods = false)
class DemoUsers {

    @Bean
    PasswordEncoder demoPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService demoUserDetails(PasswordEncoder encoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername("alice").password(encoder.encode("local-demo-only"))
                        .roles("REVIEWER").build(),
                User.withUsername("bob").password(encoder.encode("local-demo-only"))
                        .roles("USER").build());
    }
}
