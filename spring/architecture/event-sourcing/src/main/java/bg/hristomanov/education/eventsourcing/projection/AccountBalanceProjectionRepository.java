package bg.hristomanov.education.eventsourcing.projection;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountBalanceProjectionRepository
        extends JpaRepository<AccountBalanceProjection, String> {
}
