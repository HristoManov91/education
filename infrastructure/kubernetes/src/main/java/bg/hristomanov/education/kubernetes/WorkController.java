package bg.hristomanov.education.kubernetes;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Bounded, read-only simulated work for observing in-flight requests during rollout. */
@RestController
public class WorkController {
    private static final long MAX_DELAY_MILLIS = 10_000;

    @GetMapping("/api/work")
    public WorkResult work(@RequestParam(defaultValue = "1000") long delayMs)
            throws InterruptedException {
        if (delayMs < 0 || delayMs > MAX_DELAY_MILLIS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "delayMs must be between 0 and " + MAX_DELAY_MILLIS);
        }
        // Blocking is deliberate: this lab demonstrates a servlet request during SIGTERM.
        Thread.sleep(delayMs);
        return new WorkResult("completed", delayMs);
    }

    public record WorkResult(String status, long delayMs) { }
}
