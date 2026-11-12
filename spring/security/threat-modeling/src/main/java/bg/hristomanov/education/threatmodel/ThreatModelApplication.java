package bg.hristomanov.education.threatmodel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Изолирана Spring Boot лаборатория за trust boundaries и object-level authorization. */
@SpringBootApplication
public class ThreatModelApplication {
    public static void main(String[] args) {
        SpringApplication.run(ThreatModelApplication.class, args);
    }
}
