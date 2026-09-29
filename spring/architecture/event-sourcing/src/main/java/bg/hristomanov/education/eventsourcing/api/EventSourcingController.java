package bg.hristomanov.education.eventsourcing.api;

import bg.hristomanov.education.eventsourcing.application.*;
import bg.hristomanov.education.eventsourcing.domain.Account;
import bg.hristomanov.education.eventsourcing.projection.AccountBalanceProjector;
import bg.hristomanov.education.eventsourcing.projection.AccountBalanceView;
import bg.hristomanov.education.eventsourcing.projection.AccountQueryService;
import bg.hristomanov.education.eventsourcing.store.EventStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/event-sourcing")
public class EventSourcingController {

    private final AccountCommandService commandService;
    private final AccountLoader accountLoader;
    private final AccountSnapshotService snapshotService;
    private final EventStore eventStore;
    private final AccountBalanceProjector projector;
    private final AccountQueryService queryService;

    public EventSourcingController(
            AccountCommandService commandService,
            AccountLoader accountLoader,
            AccountSnapshotService snapshotService,
            EventStore eventStore,
            AccountBalanceProjector projector,
            AccountQueryService queryService
    ) {
        this.commandService = commandService;
        this.accountLoader = accountLoader;
        this.snapshotService = snapshotService;
        this.eventStore = eventStore;
        this.projector = projector;
        this.queryService = queryService;
    }

    @PostMapping("/accounts")
    public AccountStateResponse open(
            @RequestBody OpenAccountRequest request
    ) {
        Account account = commandService.open(
                request.accountId(),
                request.ownerName(),
                request.currency()
        );

        return AccountStateResponse.from(account);
    }

    @PostMapping("/accounts/{accountId}/deposits")
    public AccountStateResponse deposit(
            @PathVariable String accountId,
            @RequestBody MoneyRequest request
    ) {
        Account account = commandService.deposit(
                accountId,
                request.amount(),
                request.reference()
        );

        return AccountStateResponse.from(account);
    }

    @PostMapping("/accounts/{accountId}/withdrawals")
    public AccountStateResponse withdraw(
            @PathVariable String accountId,
            @RequestBody MoneyRequest request
    ) {
        Account account = commandService.withdraw(
                accountId,
                request.amount(),
                request.reference()
        );

        return AccountStateResponse.from(account);
    }

    @GetMapping("/accounts/{accountId}/state")
    public AccountStateResponse currentState(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "false") boolean useSnapshot
    ) {
        LoadedAccount loaded = useSnapshot
                ? accountLoader.loadUsingSnapshot(accountId)
                : accountLoader.load(accountId);

        return AccountStateResponse.from(loaded);
    }

    @GetMapping("/accounts/{accountId}/state/{version}")
    public AccountStateResponse historicalState(
            @PathVariable String accountId,
            @PathVariable long version
    ) {
        return AccountStateResponse.from(
                accountLoader.loadAtVersion(
                        accountId,
                        version
                )
        );
    }

    @GetMapping("/accounts/{accountId}/history")
    public List<StoredEventResponse> history(
            @PathVariable String accountId
    ) {
        return eventStore.readStream(accountId)
                .events()
                .stream()
                .map(StoredEventResponse::from)
                .toList();
    }

    @PostMapping("/accounts/{accountId}/snapshot")
    public AccountStateResponse snapshot(
            @PathVariable String accountId
    ) {
        return AccountStateResponse.from(
                snapshotService.createSnapshot(accountId)
        );
    }

    @GetMapping("/accounts/{accountId}/projection")
    public AccountBalanceView projection(
            @PathVariable String accountId
    ) {
        return queryService.get(accountId);
    }

    @PostMapping("/projections/next")
    public Map<String, Object> projectNext() {
        return Map.of(
                "globalPosition",
                projector.projectNext()
                        .map(Object.class::cast)
                        .orElse("NONE")
        );
    }

    @PostMapping("/projections/all")
    public Map<String, Integer> projectAll() {
        return Map.of(
                "processedEvents",
                projector.projectAllPending()
        );
    }

    @PostMapping("/projections/rebuild")
    public Map<String, Integer> rebuild() {
        return Map.of(
                "replayedEvents",
                projector.rebuild()
        );
    }
}
