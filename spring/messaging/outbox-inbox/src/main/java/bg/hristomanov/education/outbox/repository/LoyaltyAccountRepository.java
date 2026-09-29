package bg.hristomanov.education.outbox.repository;

import bg.hristomanov.education.outbox.inbox.LoyaltyAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyAccountRepository
        extends JpaRepository<LoyaltyAccount, String> {
}
