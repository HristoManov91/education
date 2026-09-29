package bg.hristomanov.education.patterns.behavioral.templatemethod;

import java.util.ArrayList;
import java.util.List;

/**
 * Template Method фиксира skeleton-а на algorithm-а, а subclasses override-ват
 * само стъпките, които реално варират.
 *
 * <p>Подходящ е, когато lifecycle-ът трябва да остане стабилен и контролиран
 * от base class-а. Ако вариращите части трябва да се комбинират динамично,
 * Strategy обикновено е по-гъвкав.</p>
 */
public abstract class AbstractOrderImportJob {

    public final ImportResult execute(String rawInput) {
        List<String> trace = new ArrayList<>();

        trace.add("load");
        String loaded = load(rawInput);

        trace.add("parse:" + formatName());
        List<String> orders = parse(loaded);

        trace.add("validate");
        validate(orders);

        trace.add("persist");
        persist(orders);

        afterImport(orders, trace);

        return new ImportResult(orders.size(), trace);
    }

    protected String load(String rawInput) {
        return rawInput;
    }

    protected abstract String formatName();

    protected abstract List<String> parse(String loaded);

    protected void validate(List<String> orders) {
        if (orders.isEmpty()) {
            throw new IllegalArgumentException("No orders to import");
        }
    }

    protected void persist(List<String> orders) {
        // Laboratory hook: реалният вариант би извикал repository/batch writer.
    }

    protected void afterImport(List<String> orders, List<String> trace) {
        // Optional hook.
    }
}
