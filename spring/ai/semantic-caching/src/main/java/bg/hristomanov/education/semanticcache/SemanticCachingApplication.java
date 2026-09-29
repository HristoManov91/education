package bg.hristomanov.education.semanticcache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SemanticCachingApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                SemanticCachingApplication.class,
                args
        );
    }
}
