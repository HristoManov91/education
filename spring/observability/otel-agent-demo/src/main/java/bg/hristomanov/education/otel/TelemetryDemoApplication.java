package bg.hristomanov.education.otel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Стартираме без OpenTelemetry SDK в business source кода.
 * Реалният OTel Java Agent се подава чрез JVM аргумент -javaagent.
 */
@SpringBootApplication
public class TelemetryDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetryDemoApplication.class, args);
    }
}
