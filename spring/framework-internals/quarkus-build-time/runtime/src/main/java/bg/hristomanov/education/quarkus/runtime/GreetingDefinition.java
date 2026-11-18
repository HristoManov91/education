package bg.hristomanov.education.quarkus.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Анотация, чиято стойност се обработва от Jandex при augmentation. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GreetingDefinition {
    String value();
}
