package bg.hristomanov.education.patterns.behavioral.memento;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Originator в Memento pattern-а.
 *
 * <p>Самият editor знае как да snapshot-не и restore-не private state-а си.
 * Caretaker-ът може да пази Memento objects, без да чете вътрешното им
 * съдържание и без да нарушава encapsulation.</p>
 */
public class PricingRuleEditor {

    private String name;
    private final LinkedHashMap<String, BigDecimal> discounts = new LinkedHashMap<>();

    public PricingRuleEditor(String name) {
        this.name = name;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void setDiscount(String segment, BigDecimal percentage) {
        discounts.put(segment, percentage);
    }

    public String name() {
        return name;
    }

    public Map<String, BigDecimal> discounts() {
        return Map.copyOf(discounts);
    }

    public Memento save() {
        return new Memento(name, discounts);
    }

    public void restore(Memento memento) {
        this.name = memento.name;
        this.discounts.clear();
        this.discounts.putAll(memento.discounts);
    }

    public static final class Memento {

        private final String name;
        private final LinkedHashMap<String, BigDecimal> discounts;

        private Memento(String name, Map<String, BigDecimal> discounts) {
            this.name = name;
            this.discounts = new LinkedHashMap<>(discounts);
        }
    }
}
