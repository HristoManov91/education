package bg.hristomanov.education.quarkus.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/** Immutable BuildItem — резултатът от анализа се предава към генератора. */
public final class GreetingModelBuildItem extends SimpleBuildItem {
    private final String message;

    public GreetingModelBuildItem(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
