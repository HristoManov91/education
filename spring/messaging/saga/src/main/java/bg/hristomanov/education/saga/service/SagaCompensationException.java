package bg.hristomanov.education.saga.service;

public class SagaCompensationException extends RuntimeException {

    public SagaCompensationException(String message) {
        super(message);
    }
}
