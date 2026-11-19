package bg.hristomanov.education.patterns.mastery.model;

import java.util.List;

/** HTTP input. Rules validate mandatory fields before business operations. */
public record ReportRequest(String reference, String owner, List<String> items, String format) {
    public ReportRequest {
        // Keep the input immutable without hiding invalid data from the validation chain.
        if (items != null) {
            if (items.stream().anyMatch(item -> item == null)) {
                throw new IllegalArgumentException("items must not contain null");
            }
            items = List.copyOf(items);
        }
    }
}
