package bg.hristomanov.education.jfr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Стартиращ клас за JFR troubleshooting лабораторията.
 *
 * <p>Приложението нарочно е обикновен Spring MVC service. Идеята е JFR да се упражнява
 * върху познат production-like backend, а не върху изолиран console пример.</p>
 */
@SpringBootApplication
public class JfrTroubleshootingApplication {

    public static void main(String[] args) {
        SpringApplication.run(JfrTroubleshootingApplication.class, args);
    }
}
