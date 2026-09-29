package bg.hristomanov.education.eventsourcing.projection;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountQueryService {

    private final AccountBalanceProjectionRepository repository;

    public AccountQueryService(
            AccountBalanceProjectionRepository repository
    ) {
        this.repository = repository;
    }

    public AccountBalanceView get(String accountId) {
        return repository.findById(accountId)
                .map(AccountBalanceView::from)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Read projection not available for account: "
                                        + accountId
                        )
                );
    }
}
