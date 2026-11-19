package bg.hristomanov.education.patterns.mastery;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Invalid user input is 400. Downstream failures remain 5xx (not falsified as success). */
@RestControllerAdvice
public class ReportErrorAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}
