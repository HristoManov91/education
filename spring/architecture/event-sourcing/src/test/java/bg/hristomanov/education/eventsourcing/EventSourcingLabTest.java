package bg.hristomanov.education.eventsourcing;

import bg.hristomanov.education.eventsourcing.application.*;
import bg.hristomanov.education.eventsourcing.domain.Account;
import bg.hristomanov.education.eventsourcing.domain.MoneyDeposited;
import bg.hristomanov.education.eventsourcing.projection.*;
import bg.hristomanov.education.eventsourcing.snapshot.AccountSnapshotRepository;
import bg.hristomanov.education.eventsourcing.store.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EventSourcingLabTest {

    private final AccountCommandService commandService;
    private final AccountLoader accountLoader;
    private final AccountSnapshotService snapshotService;
    private final EventStore eventStore;
    private final StoredEventRepository eventRepository;
    private final AccountSnapshotRepository snapshotRepository;
    private final AccountBalanceProjectionRepository projectionRepository;
    private final ProjectionCheckpointRepository checkpointRepository;
    private final AccountBalanceProjector projector;
    private final AccountQueryService queryService;

    @Autowired
    EventSourcingLabTest(
            AccountCommandService commandService,
            AccountLoader accountLoader,
            AccountSnapshotService snapshotService,
            EventStore eventStore,
            StoredEventRepository eventRepository,
            AccountSnapshotRepository snapshotRepository,
            AccountBalanceProjectionRepository projectionRepository,
            ProjectionCheckpointRepository checkpointRepository,
            AccountBalanceProjector projector,
            AccountQueryService queryService
    ) {
        this.commandService = commandService;
        this.accountLoader = accountLoader;
        this.snapshotService = snapshotService;
        this.eventStore = eventStore;
        this.eventRepository = eventRepository;
        this.snapshotRepository = snapshotRepository;
        this.projectionRepository = projectionRepository;
        this.checkpointRepository = checkpointRepository;
        this.projector = projector;
        this.queryService = queryService;
    }

    @BeforeEach
    void clean() {
        checkpointRepository.deleteAll();
        projectionRepository.deleteAll();
        snapshotRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void currentStateIsDerivedEntirelyByReplayingTheEventStream() {
        commandService.open(
                "ACC-REPLAY",
                "Ada",
                "EUR"
        );
        commandService.deposit(
                "ACC-REPLAY",
                new BigDecimal("150.00"),
                "salary"
        );
        commandService.withdraw(
                "ACC-REPLAY",
                new BigDecimal("20.00"),
                "groceries"
        );

        LoadedAccount loaded =
                accountLoader.load("ACC-REPLAY");

        assertThat(loaded.account().balance())
                .isEqualByComparingTo("130.00");
        assertThat(loaded.account().version()).isEqualTo(2);
        assertThat(loaded.replayedEventCount()).isEqualTo(3);
        assertThat(eventRepository.count()).isEqualTo(3);

        /*
         * Няма current-state Account table. Aggregate state е working copy,
         * derived от трите persisted facts.
         */
        assertThat(projectionRepository.count()).isZero();
    }

    @Test
    void historicalStateCanBeReconstructedAtAnEarlierStreamVersion() {
        commandService.open(
                "ACC-HISTORY",
                "Grace",
                "EUR"
        );
        commandService.deposit(
                "ACC-HISTORY",
                new BigDecimal("150.00"),
                "salary"
        );
        commandService.withdraw(
                "ACC-HISTORY",
                new BigDecimal("20.00"),
                "groceries"
        );

        Account historical = accountLoader
                .loadAtVersion("ACC-HISTORY", 1)
                .account();

        Account current = accountLoader
                .load("ACC-HISTORY")
                .account();

        assertThat(historical.version()).isEqualTo(1);
        assertThat(historical.balance())
                .isEqualByComparingTo("150.00");

        assertThat(current.version()).isEqualTo(2);
        assertThat(current.balance())
                .isEqualByComparingTo("130.00");
    }

    @Test
    void rejectedCommandDoesNotCreateAnEvent() {
        commandService.open(
                "ACC-REJECT",
                "Linus",
                "EUR"
        );

        assertThatThrownBy(() ->
                commandService.withdraw(
                        "ACC-REJECT",
                        new BigDecimal("100.00"),
                        "too-much"
                )
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");

        EventStream history =
                eventStore.readStream("ACC-REJECT");

        assertThat(history.events()).hasSize(1);
        assertThat(history.version()).isZero();
        assertThat(history.events().getFirst().eventType())
                .isEqualTo("AccountOpened");
    }

    @Test
    void staleExpectedVersionIsRejectedInsteadOfOverwritingHistory() {
        commandService.open(
                "ACC-CONCURRENCY",
                "Margaret",
                "EUR"
        );

        eventStore.append(
                "ACC-CONCURRENCY",
                0,
                List.of(
                        new MoneyDeposited(
                                new BigDecimal("50.00"),
                                "writer-1"
                        )
                )
        );

        assertThatThrownBy(() ->
                eventStore.append(
                        "ACC-CONCURRENCY",
                        0,
                        List.of(
                                new MoneyDeposited(
                                        new BigDecimal("25.00"),
                                        "stale-writer"
                                )
                        )
                )
        )
                .isInstanceOf(OptimisticConcurrencyException.class)
                .hasMessageContaining("expected version 0")
                .hasMessageContaining("actual version is 1");

        LoadedAccount loaded =
                accountLoader.load("ACC-CONCURRENCY");

        assertThat(loaded.account().balance())
                .isEqualByComparingTo("50.00");
        assertThat(loaded.account().version()).isEqualTo(1);
        assertThat(eventRepository.count()).isEqualTo(2);
    }

    @Test
    void readProjectionCanBeDeletedAndRebuiltFromTheEventStore() {
        commandService.open(
                "ACC-PROJECTION",
                "Barbara",
                "EUR"
        );
        commandService.deposit(
                "ACC-PROJECTION",
                new BigDecimal("200.00"),
                "salary"
        );
        commandService.withdraw(
                "ACC-PROJECTION",
                new BigDecimal("35.00"),
                "shopping"
        );

        assertThatThrownBy(() ->
                queryService.get("ACC-PROJECTION")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Read projection not available");

        assertThat(projector.projectAllPending()).isEqualTo(3);

        AccountBalanceView projected =
                queryService.get("ACC-PROJECTION");

        assertThat(projected.balance())
                .isEqualByComparingTo("165.00");
        assertThat(projected.sourceVersion()).isEqualTo(2);

        projectionRepository.deleteAll();
        checkpointRepository.deleteAll();

        assertThat(projectionRepository.count()).isZero();

        int replayed = projector.rebuild();

        assertThat(replayed).isEqualTo(3);

        AccountBalanceView rebuilt =
                queryService.get("ACC-PROJECTION");

        assertThat(rebuilt).isEqualTo(projected);
    }

    @Test
    void snapshotReducesReplayWorkWithoutBecomingSourceOfTruth() {
        commandService.open(
                "ACC-SNAPSHOT",
                "Edsger",
                "EUR"
        );

        for (int index = 0; index < 5; index++) {
            commandService.deposit(
                    "ACC-SNAPSHOT",
                    new BigDecimal("10.00"),
                    "before-snapshot-" + index
            );
        }

        snapshotService.createSnapshot("ACC-SNAPSHOT");

        for (int index = 0; index < 3; index++) {
            commandService.deposit(
                    "ACC-SNAPSHOT",
                    new BigDecimal("10.00"),
                    "after-snapshot-" + index
            );
        }

        LoadedAccount fullReplay =
                accountLoader.load("ACC-SNAPSHOT");

        LoadedAccount snapshotReplay =
                accountLoader.loadUsingSnapshot("ACC-SNAPSHOT");

        assertThat(fullReplay.account().balance())
                .isEqualByComparingTo("80.00");
        assertThat(snapshotReplay.account().balance())
                .isEqualByComparingTo("80.00");
        assertThat(snapshotReplay.account().version())
                .isEqualTo(fullReplay.account().version());

        assertThat(fullReplay.replayedEventCount()).isEqualTo(9);
        assertThat(snapshotReplay.snapshotUsed()).isTrue();
        assertThat(snapshotReplay.replayedEventCount()).isEqualTo(3);

        /*
         * Snapshot-ът може да бъде изтрит и state остава възстановим.
         */
        snapshotRepository.deleteAll();

        LoadedAccount afterSnapshotLoss =
                accountLoader.load("ACC-SNAPSHOT");

        assertThat(afterSnapshotLoss.account().balance())
                .isEqualByComparingTo("80.00");
        assertThat(afterSnapshotLoss.replayedEventCount()).isEqualTo(9);
    }

    @Test
    void persistedEventsCarryStableOrderingAndSchemaMetadata() {
        commandService.open(
                "ACC-METADATA",
                "Donald",
                "EUR"
        );
        commandService.deposit(
                "ACC-METADATA",
                new BigDecimal("25.00"),
                "deposit"
        );

        List<StoredEvent> history =
                eventStore.readStream("ACC-METADATA").events();

        assertThat(history)
                .extracting(StoredEvent::streamVersion)
                .containsExactly(0L, 1L);

        assertThat(history)
                .extracting(StoredEvent::eventSchemaVersion)
                .containsOnly(AccountEventCodec.CURRENT_SCHEMA_VERSION);

        assertThat(history.get(0).globalPosition())
                .isLessThan(history.get(1).globalPosition());

        assertThat(history)
                .extracting(StoredEvent::eventId)
                .doesNotHaveDuplicates();
    }
}
