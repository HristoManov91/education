package bg.hristomanov.education.aitesting;

import bg.hristomanov.education.aitesting.domain.RegistrationPolicy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class TestingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestingApplication.class, args);
    }

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }

    @Bean
    RegistrationPolicy registrationPolicy() {
        return new RegistrationPolicy();
    }
}
