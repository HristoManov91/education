package bg.hristomanov.education.portmodulith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * One deployment containing explicit, independently verifiable domain modules.
 * It is deliberately not an HTTP/API gateway or a running Kafka installation.
 */
@SpringBootApplication
public class HarbourModulithApplication {

    public static void main(String[] args) {
        SpringApplication.run(HarbourModulithApplication.class, args);
    }
}
