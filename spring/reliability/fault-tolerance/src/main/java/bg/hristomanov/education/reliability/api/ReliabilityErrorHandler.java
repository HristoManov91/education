package bg.hristomanov.education.reliability.api;

import bg.hristomanov.education.reliability.downstream.DownstreamTimeoutException;
import bg.hristomanov.education.reliability.downstream.PermanentDownstreamException;
import bg.hristomanov.education.reliability.downstream.TransientDownstreamException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ReliabilityErrorHandler {

    @ExceptionHandler(DownstreamTimeoutException.class)
    public ResponseEntity<Map<String, String>> timeout(
            DownstreamTimeoutException exception
    ) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Map.of("error", "DOWNSTREAM_TIMEOUT", "message", exception.getMessage()));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<Map<String, String>> circuitOpen(
            CallNotPermittedException exception
    ) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "CIRCUIT_OPEN", "message", exception.getMessage()));
    }

    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<Map<String, String>> bulkheadFull(
            BulkheadFullException exception
    ) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "BULKHEAD_FULL", "message", exception.getMessage()));
    }

    @ExceptionHandler(TransientDownstreamException.class)
    public ResponseEntity<Map<String, String>> transientFailure(
            TransientDownstreamException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "TRANSIENT_DOWNSTREAM", "message", exception.getMessage()));
    }

    @ExceptionHandler(PermanentDownstreamException.class)
    public ResponseEntity<Map<String, String>> permanentFailure(
            PermanentDownstreamException exception
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "PERMANENT_DOWNSTREAM", "message", exception.getMessage()));
    }
}
