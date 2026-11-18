package bg.hristomanov.education.quarkus.app;

import bg.hristomanov.education.quarkus.runtime.GreetingDefinition;

/**
 * Използва се само от build-time Jandex процесора.
 * Не ни е нужен runtime reflection scanner, който да зарежда този клас.
 */
@GreetingDefinition("Hello from build-time augmentation")
public final class GreetingModel {
}
