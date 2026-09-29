package bg.hristomanov.education.hexagonal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Outermost composition root.
 *
 * <p>The application class sits at the common package root so Spring can
 * discover the REST/JPA adapters. The core itself contains no Spring annotations.</p>
 */
@SpringBootApplication
public class HexagonalArchitectureApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                HexagonalArchitectureApplication.class,
                args
        );
    }
}
