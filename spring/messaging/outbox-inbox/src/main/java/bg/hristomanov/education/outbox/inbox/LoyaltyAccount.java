package bg.hristomanov.education.outbox.inbox;

import jakarta.persistence.*;

@Entity
@Table(name = "loyalty_accounts")
public class LoyaltyAccount {

    @Id
    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(nullable = false)
    private int points;

    protected LoyaltyAccount() {
    }

    public LoyaltyAccount(String customerId) {
        this.customerId = customerId;
    }

    public void addPoints(int pointsToAdd) {
        this.points += pointsToAdd;
    }

    public String getCustomerId() {
        return customerId;
    }

    public int getPoints() {
        return points;
    }
}
