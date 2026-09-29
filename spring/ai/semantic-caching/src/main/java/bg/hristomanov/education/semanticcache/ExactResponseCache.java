package bg.hristomanov.education.semanticcache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExactResponseCache {

    private final Map<String, String> values =
            new HashMap<>();

    public void put(
            String query,
            CacheContext context,
            String response
    ) {
        values.put(
                key(query, context),
                response
        );
    }

    public Optional<String> get(
            String query,
            CacheContext context
    ) {
        return Optional.ofNullable(
                values.get(key(query, context))
        );
    }

    private String key(
            String query,
            CacheContext context
    ) {
        return context.fingerprint()
                + "|"
                + query.trim();
    }
}
