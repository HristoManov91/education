package bg.hristomanov.education.hperf;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only SQL capture: lets a real Hibernate 7.4 query prove database-level
 * pagination, instead of checking only that the returned objects look correct.
 */
public final class SqlCaptureInspector implements StatementInspector {
    private static final List<String> SQL = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String statement) {
        SQL.add(statement);
        return statement; // Do not alter application SQL.
    }

    public static void clear() { SQL.clear(); }
    public static List<String> statements() { return List.copyOf(SQL); }
}
