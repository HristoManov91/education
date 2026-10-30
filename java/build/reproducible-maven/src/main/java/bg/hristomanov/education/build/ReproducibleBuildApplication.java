package bg.hristomanov.education.build;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReproducibleBuildApplication {

    public static void main(String[] args) {
        // This is a tiny executable Spring Boot JAR with no external services.
        SpringApplication.run(ReproducibleBuildApplication.class, args).close();
    }
}
