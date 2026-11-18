package bg.hristomanov.education.quarkus.deployment;

import java.util.List;

/** Проверява модела преди bytecode generation — без runtime fallback. */
final class GreetingRules {
    private GreetingRules() {
    }

    static String requireSingle(List<String> messages) {
        if (messages.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one @GreetingDefinition, found " + messages.size());
        }
        String message = messages.getFirst();
        if (message == null || message.isBlank() || message.length() > 200) {
            throw new IllegalStateException("Greeting must contain 1..200 non-blank characters");
        }
        return message;
    }
}
