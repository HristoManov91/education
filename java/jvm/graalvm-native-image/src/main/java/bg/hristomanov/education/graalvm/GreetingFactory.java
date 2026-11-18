package bg.hristomanov.education.graalvm;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Reflection boundary: allowlist и типови проверки преди инстанциране.
 *
 * Reachability metadata помага на Native Image да включи нужните елементи,
 * но не е security sandbox за произволен неподписан plugin code.
 */
public final class GreetingFactory {
    private static final String APPROVED_PROVIDER =
            "bg.hristomanov.education.graalvm.ReflectionGreeting";

    private GreetingFactory() {
    }

    public static GreetingProvider staticProvider() {
        return new StaticGreeting();
    }

    public static GreetingProvider reflectiveProvider(String className) {
        if (!APPROVED_PROVIDER.equals(className)) {
            throw new IllegalArgumentException("Provider is not allowlisted: " + className);
        }
        try {
            Class<?> loadedClass = Class.forName(className);
            if (!GreetingProvider.class.isAssignableFrom(loadedClass)) {
                throw new IllegalArgumentException("Provider does not implement GreetingProvider");
            }
            Constructor<?> constructor = loadedClass.getDeclaredConstructor();
            Object instance = constructor.newInstance();
            return (GreetingProvider) instance;
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                 | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Provider cannot be initialized", exception);
        }
    }
}
