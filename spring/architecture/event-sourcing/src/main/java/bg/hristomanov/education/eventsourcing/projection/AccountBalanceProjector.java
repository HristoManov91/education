package bg.hristomanov.education.eventsourcing.projection;

import bg.hristomanov.education.eventsourcing.domain.*;
import bg.hristomanov.education.eventsourcing.store.EventStore;
import bg.hristomanov.education.eventsourcing.store.StoredEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Derived read model projector.
 *
 * <p>The projection is disposable/rebuildable. The event store remains the
 * authoritative source of truth.</p>
 */
@Service
public class AccountBalanceProjector {

    public static final String PROJECTION_NAME =
            "account-balance-v1";

    private static final long BEFORE_FIRST_EVENT = -1L;

    private final EventStore eventStore;
    private final AccountBalanceProjectionRepository projectionRepository;
    private final ProjectionCheckpointRepository checkpointRepository;

    public AccountBalanceProjector(
            EventStore eventStore,
            AccountBalanceProjectionRepository projectionRepository,
            ProjectionCheckpointRepository checkpointRepository
    ) {
        this.eventStore = eventStore;
        this.projectionRepository = projectionRepository;
        this.checkpointRepository = checkpointRepository;
    }

    @Transactional
    public Optional<Long> projectNext() {
        ProjectionCheckpoint checkpoint = checkpointRepository
                .findById(PROJECTION_NAME)
                .orElseGet(() ->
                        new ProjectionCheckpoint(
                                PROJECTION_NAME,
                                BEFORE_FIRST_EVENT
                        )
                );

        List<StoredEvent> pending =
                eventStore.readAllAfterPosition(
                        checkpoint.getLastGlobalPosition()
                );

        if (pending.isEmpty()) {
            return Optional.empty();
        }

        StoredEvent next = pending.getFirst();

        apply(next);
        checkpoint.advanceTo(next.globalPosition());
        checkpointRepository.save(checkpoint);

        return Optional.of(next.globalPosition());
    }

    @Transactional
    public int projectAllPending() {
        ProjectionCheckpoint checkpoint = checkpointRepository
                .findById(PROJECTION_NAME)
                .orElseGet(() ->
                        new ProjectionCheckpoint(
                                PROJECTION_NAME,
                                BEFORE_FIRST_EVENT
                        )
                );

        List<StoredEvent> pending =
                eventStore.readAllAfterPosition(
                        checkpoint.getLastGlobalPosition()
                );

        for (StoredEvent event : pending) {
            apply(event);
            checkpoint.advanceTo(event.globalPosition());
        }

        checkpointRepository.save(checkpoint);
        return pending.size();
    }

    @Transactional
    public int rebuild() {
        projectionRepository.deleteAll();
        checkpointRepository.deleteAll();

        List<StoredEvent> history =
                eventStore.readAllAfterPosition(BEFORE_FIRST_EVENT);

        ProjectionCheckpoint checkpoint =
                new ProjectionCheckpoint(
                        PROJECTION_NAME,
                        BEFORE_FIRST_EVENT
                );

        for (StoredEvent event : history) {
            apply(event);
            checkpoint.advanceTo(event.globalPosition());
        }

        checkpointRepository.save(checkpoint);
        return history.size();
    }

    private void apply(StoredEvent storedEvent) {
        Instant projectedAt = Instant.now();

        switch (storedEvent.event()) {
            case AccountOpened opened ->
                    projectionRepository.save(
                            new AccountBalanceProjection(
                                    opened.accountId(),
                                    opened.ownerName(),
                                    opened.currency(),
                                    storedEvent.streamVersion(),
                                    projectedAt
                            )
                    );

            case MoneyDeposited deposited -> {
                AccountBalanceProjection projection =
                        requiredProjection(storedEvent.streamId());

                projection.deposit(
                        deposited.amount(),
                        storedEvent.streamVersion(),
                        projectedAt
                );
            }

            case MoneyWithdrawn withdrawn -> {
                AccountBalanceProjection projection =
                        requiredProjection(storedEvent.streamId());

                projection.withdraw(
                        withdrawn.amount(),
                        storedEvent.streamVersion(),
                        projectedAt
                );
            }
        }
    }

    private AccountBalanceProjection requiredProjection(
            String accountId
    ) {
        return projectionRepository.findById(accountId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Projection received account event before AccountOpened for "
                                        + accountId
                        )
                );
    }
}
