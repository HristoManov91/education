package bg.hristomanov.education.aitesting.service;

public final class RegistrationRejectedException extends RuntimeException {

    public RegistrationRejectedException(String reason) {
        super(reason);
    }
}
