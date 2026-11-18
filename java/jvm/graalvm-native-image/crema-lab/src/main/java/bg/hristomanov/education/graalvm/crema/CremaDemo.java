package bg.hristomanov.education.graalvm.crema;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicLong;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.description.modifier.Visibility;

/**
 * Byte Buddy генерира клас с ново име при всяко извикване.
 * JVM тестът работи без Crema; native вариантът изисква експериментална
 * runtime class loading поддръжка и отделна интеграционна проверка.
 */
public final class CremaDemo {
    private static final AtomicLong IDS = new AtomicLong();

    private CremaDemo() {
    }

    public static Announcement generate(String speaker, String event)
            throws ReflectiveOperationException {
        validate(speaker, "speaker");
        validate(event, "event");
        String className = "bg.hristomanov.education.graalvm.crema.GeneratedGreeting"
                + IDS.incrementAndGet();
        String text = "Hello " + speaker + " at " + event;

        DynamicType.Unloaded<?> generated = new ByteBuddy()
                .subclass(Object.class)
                .name(className)
                .defineMethod("announce", String.class, Visibility.PUBLIC)
                .intercept(FixedValue.value(text))
                .make();

        Class<?> clazz = generated.load(CremaDemo.class.getClassLoader(),
                ClassLoadingStrategy.Default.WRAPPER).getLoaded();
        Object instance;
        try {
            instance = clazz.getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Generated constructor failed", exception);
        }
        String announcement = (String) clazz.getMethod("announce").invoke(instance);
        return new Announcement(clazz.getName(), announcement);
    }

    private static void validate(String input, String field) {
        if (input == null || input.isBlank() || input.length() > 60) {
            throw new IllegalArgumentException("Invalid " + field);
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        String speaker = args.length > 0 ? args[0] : "Alina";
        String event = args.length > 1 ? args[1] : "Devoxx";
        if (args.length > 2) {
            throw new IllegalArgumentException("Expected speaker and event only");
        }
        Announcement announcement = generate(speaker, event);
        System.out.println("READY " + announcement.text()
                + " generatedClass=" + announcement.generatedClassName());
    }

    public record Announcement(String generatedClassName, String text) {
    }
}
