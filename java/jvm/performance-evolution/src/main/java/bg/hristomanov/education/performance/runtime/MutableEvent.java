package bg.hristomanov.education.performance.runtime;

import java.util.Objects;

/**
 * Демонстрационен mutable buffer (изменяем обект), НЕ DTO за споделяне
 * между заявки или нишки. Reuse променя identity/ownership semantics.
 */
public final class MutableEvent {

    private String key;
    private String detail;
    private long amount;

    public MutableEvent(String key, String detail, long amount) {
        overwrite(key, detail, amount);
    }

    public void overwrite(String key, String detail, long amount) {
        // Валидираме всички входове преди първата mutation, за да не оставим
        // частично променен обект при exception.
        String nextKey = Objects.requireNonNull(key, "key");
        String nextDetail = Objects.requireNonNull(detail, "detail");
        this.key = nextKey;
        this.detail = nextDetail;
        this.amount = amount;
    }

    public String key() {
        return key;
    }

    public String detail() {
        return detail;
    }

    public long amount() {
        return amount;
    }
}
