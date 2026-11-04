package bg.hristomanov.education.kubernetes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only explicitly safe configuration appears in responses; Kubernetes Secrets are never echoed. */
@RestController
public class HelloController {
    private final String greeting;
    private final String podName;

    public HelloController(@Value("${app.greeting:Hello from Kubernetes}") String greeting,
                           @Value("${app.pod-name:local}") String podName) {
        this.greeting = greeting;
        this.podName = podName;
    }

    @GetMapping("/api/hello")
    public Greeting hello() {
        return new Greeting(greeting, podName, Runtime.version().feature());
    }

    public record Greeting(String message, String pod, int javaFeature) { }
}
