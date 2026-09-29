package bg.hristomanov.education.reliability.downstream;

public class DownstreamTimeoutException extends RuntimeException {

    public DownstreamTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
