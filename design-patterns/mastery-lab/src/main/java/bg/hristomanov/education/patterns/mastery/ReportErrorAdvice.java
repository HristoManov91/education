package bg.hristomanov.education.patterns.mastery;

import bg.hristomanov.education.patterns.mastery.archive.ArchiveConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Invalid user input is 400. Duplicate writes are 409; unrelated downstream failures remain errors. */
@RestControllerAdvice
public class ReportErrorAdvice {
    @ExceptionHandler(ArchiveConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(ArchiveConflictException ex) {
        return Map.of("error", "archive reference already exists");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}
