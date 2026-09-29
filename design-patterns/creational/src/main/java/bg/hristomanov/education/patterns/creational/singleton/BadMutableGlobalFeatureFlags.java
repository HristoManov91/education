package bg.hristomanov.education.patterns.creational.singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BAD пример: global mutable singleton.
 *
 * <p>Изглежда удобно, защото е достъпен отвсякъде, но dependency-то вече не се
 * вижда в constructor-а на consumer-а. Тестовете могат да си влияят взаимно,
 * state-ът преживява по-дълго от конкретния use case и всяка промяна изисква
 * да мислим за thread-safety.</p>
 */
public final class BadMutableGlobalFeatureFlags {

    private static final BadMutableGlobalFeatureFlags INSTANCE = new BadMutableGlobalFeatureFlags();

    private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

    private BadMutableGlobalFeatureFlags() {
    }

    public static BadMutableGlobalFeatureFlags getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled(String flagName) {
        return flags.getOrDefault(flagName, false);
    }

    public void setEnabled(String flagName, boolean enabled) {
        flags.put(flagName, enabled);
    }

    public void clear() {
        flags.clear();
    }
}
