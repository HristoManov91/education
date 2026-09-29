package bg.hristomanov.education.patterns.creational.builder;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Builder е полезен, когато object construction има много optional параметри,
 * инварианти или четимостта на call site-а е по-важна от краткия constructor.
 */
public final class ReportRequest {

    private final LocalDate from;
    private final LocalDate to;
    private final boolean includeDetails;
    private final boolean includeArchived;
    private final String format;
    private final Set<String> filters;

    private ReportRequest(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
        this.includeDetails = builder.includeDetails;
        this.includeArchived = builder.includeArchived;
        this.format = builder.format;
        this.filters = Set.copyOf(builder.filters);
    }

    public static Builder between(LocalDate from, LocalDate to) {
        return new Builder(from, to);
    }

    public LocalDate from() {
        return from;
    }

    public LocalDate to() {
        return to;
    }

    public boolean includeDetails() {
        return includeDetails;
    }

    public boolean includeArchived() {
        return includeArchived;
    }

    public String format() {
        return format;
    }

    public Set<String> filters() {
        return filters;
    }

    public static final class Builder {

        private final LocalDate from;
        private final LocalDate to;
        private boolean includeDetails;
        private boolean includeArchived;
        private String format = "CSV";
        private final Set<String> filters = new LinkedHashSet<>();

        private Builder(LocalDate from, LocalDate to) {
            this.from = Objects.requireNonNull(from);
            this.to = Objects.requireNonNull(to);
        }

        public Builder includeDetails() {
            this.includeDetails = true;
            return this;
        }

        public Builder includeArchived() {
            this.includeArchived = true;
            return this;
        }

        public Builder format(String format) {
            this.format = Objects.requireNonNull(format);
            return this;
        }

        public Builder addFilter(String filter) {
            this.filters.add(Objects.requireNonNull(filter));
            return this;
        }

        public ReportRequest build() {
            if (to.isBefore(from)) {
                throw new IllegalStateException("'to' must not be before 'from'");
            }
            return new ReportRequest(this);
        }
    }
}
