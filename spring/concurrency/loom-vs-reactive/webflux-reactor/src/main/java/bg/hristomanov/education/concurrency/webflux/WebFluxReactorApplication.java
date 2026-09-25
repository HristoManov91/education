package bg.hristomanov.education.concurrency.webflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WebFluxReactorApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                WebFluxReactorApplication.class,
                args);
    }
}
