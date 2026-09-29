package bg.hristomanov.education.patterns.creational.singleton;

import java.util.Set;

/**
 * GoF Singleton в най-сигурната Java форма: enum instance.
 *
 * <p>Този пример нарочно е stateless/read-only. Singleton с mutable business state
 * често се превръща в скрита глобална зависимост, която затруднява тестове,
 * concurrency reasoning и lifecycle management.</p>
 *
 * <p>Важно за Spring: default singleton bean scope означава една bean instance
 * на ApplicationContext, а НЕ задължително един JVM-wide GoF Singleton.</p>
 */
public enum CountryCodeRegistry {

    INSTANCE;

    private final Set<String> supportedCountryCodes = Set.of("BG", "DE", "FR", "US");

    public boolean isSupported(String countryCode) {
        return supportedCountryCodes.contains(countryCode);
    }
}
