package bg.hristomanov.education.reliability.downstream;

public class TransientDownstreamException extends RuntimeException {

    public TransientDownstreamException(String message) {
        super(message);
    }
}
