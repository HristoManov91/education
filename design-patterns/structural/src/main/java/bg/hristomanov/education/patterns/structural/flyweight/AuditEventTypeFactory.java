package bg.hristomanov.education.patterns.structural.flyweight;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory/cache за Flyweight objects.
 *
 * <p>В production това си струва само ако имаме огромен брой objects и
 * повторяемата intrinsic state е достатъчно голяма, за да оправдае
 * допълнителната indirection.</p>
 */
public class AuditEventTypeFactory {

    private final ConcurrentHashMap<String, AuditEventType> types = new ConcurrentHashMap<>();

    public AuditEventType get(
            String code,
            String severity,
            String category
    ) {
        String key = code + "|" + severity + "|" + category;
        return types.computeIfAbsent(
                key,
                ignored -> new AuditEventType(code, severity, category)
        );
    }

    public int cachedTypeCount() {
        return types.size();
    }

    public Map<String, AuditEventType> snapshot() {
        return Map.copyOf(types);
    }
}
