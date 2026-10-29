package bg.hristomanov.education.boot4;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Учебно Spring Boot 4.1 приложение. Активираме proxy-based (чрез Spring proxy)
 * retry/concurrency само веднъж, в composition root-а.
 */
@SpringBootApplication
@EnableResilientMethods
public class BootifulSpringBoot4Application {

    public static void main(String[] args) {
        SpringApplication.run(BootifulSpringBoot4Application.class, args);
    }
}
