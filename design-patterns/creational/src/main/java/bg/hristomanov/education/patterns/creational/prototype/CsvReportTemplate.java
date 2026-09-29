package bg.hristomanov.education.patterns.creational.prototype;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Concrete Prototype с copy constructor.
 *
 * <p>Колекциите се копират отделно. Ако просто копирахме references,
 * оригиналът и clone-ът щяха да споделят mutable state и това вече би било
 * shallow copy (повърхностно копиране), което често е източник на bugs.</p>
 */
public class CsvReportTemplate implements ReportTemplate {

    private final String name;
    private final LinkedHashSet<String> columns;
    private final LinkedHashMap<String, String> filters;

    public CsvReportTemplate(
            String name,
            Set<String> columns,
            Map<String, String> filters
    ) {
        this.name = name;
        this.columns = new LinkedHashSet<>(columns);
        this.filters = new LinkedHashMap<>(filters);
    }

    private CsvReportTemplate(CsvReportTemplate source) {
        this.name = source.name;
        this.columns = new LinkedHashSet<>(source.columns);
        this.filters = new LinkedHashMap<>(source.filters);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<String> columns() {
        return Set.copyOf(columns);
    }

    @Override
    public Map<String, String> filters() {
        return Map.copyOf(filters);
    }

    @Override
    public void putFilter(String key, String value) {
        filters.put(key, value);
    }

    @Override
    public ReportTemplate copy() {
        return new CsvReportTemplate(this);
    }
}
