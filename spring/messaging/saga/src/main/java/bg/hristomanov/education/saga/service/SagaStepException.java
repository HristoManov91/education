package bg.hristomanov.education.saga.service;

public class SagaStepException extends RuntimeException {

    public SagaStepException(String message) {
        super(message);
    }
}
