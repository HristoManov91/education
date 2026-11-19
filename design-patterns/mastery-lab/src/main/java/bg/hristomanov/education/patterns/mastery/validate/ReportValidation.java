package bg.hristomanov.education.patterns.mastery.validate;

import bg.hristomanov.education.patterns.mastery.model.ReportRequest;
import java.util.List;
import java.util.Objects;

/**
 * A short-circuit validation chain. Each rule may reject the request, or let it continue.
 * In a real system, integrate Bean Validation for DTO constraints where appropriate.
 */
public final class ReportValidation {
    @FunctionalInterface
    public interface Rule { void verify(ReportRequest request); }

    private final List<Rule> rules;

    public ReportValidation(List<Rule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public void verify(ReportRequest request) {
        Objects.requireNonNull(request, "request");
        for (Rule rule : rules) {
            rule.verify(request); // failure stops the chain; later steps are not invoked
        }
    }

    public static Rule requiredMetadata() {
        return request -> {
            if (request.reference() == null || request.reference().isBlank()
                    || request.owner() == null || request.owner().isBlank()) {
                throw new IllegalArgumentException("reference and owner are required");
            }
        };
    }

    public static Rule nonEmptyItems() {
        return request -> {
            if (request.items() == null || request.items().isEmpty()
                    || request.items().stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("at least one nonblank item is required");
            }
        };
    }
}
