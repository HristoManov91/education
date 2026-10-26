package bg.hristomanov.education.aitesting.api;

import bg.hristomanov.education.aitesting.service.RegistrationRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RegistrationErrorAdvice {

    @ExceptionHandler(RegistrationRejectedException.class)
    public ProblemDetail handleBusinessRejection(RegistrationRejectedException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }
}
