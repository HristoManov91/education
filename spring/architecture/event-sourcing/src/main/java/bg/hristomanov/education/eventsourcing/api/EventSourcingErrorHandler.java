package bg.hristomanov.education.eventsourcing.api;

import bg.hristomanov.education.eventsourcing.store.OptimisticConcurrencyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class EventSourcingErrorHandler {

    @ExceptionHandler(OptimisticConcurrencyException.class)
    public ResponseEntity<Map<String, String>> concurrency(
            OptimisticConcurrencyException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class
    })
    public ResponseEntity<Map<String, String>> badRequest(
            RuntimeException exception
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", exception.getMessage()));
    }
}
