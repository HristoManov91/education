package bg.hristomanov.education.graalvm;

/** Предварително известна на компилатора реализация. */
public final class StaticGreeting implements GreetingProvider {
    @Override
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
