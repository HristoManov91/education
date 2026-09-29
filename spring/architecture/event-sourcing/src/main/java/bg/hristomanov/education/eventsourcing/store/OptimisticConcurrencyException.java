package bg.hristomanov.education.eventsourcing.store;

public class OptimisticConcurrencyException extends RuntimeException {

    public OptimisticConcurrencyException(
            String streamId,
            long expectedVersion,
            long actualVersion
    ) {
        super(
                "Stream "
                        + streamId
                        + " expected version "
                        + expectedVersion
                        + " but actual version is "
                        + actualVersion
        );
    }
}
