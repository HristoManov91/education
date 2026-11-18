package bg.hristomanov.education.graalvm;

/**
 * Зареждаме класа по име при изпълнение; native-image може да не го
 * открие без reachability metadata при build.
 */
public final class ReflectionGreeting implements GreetingProvider {
    public ReflectionGreeting() {
    }

    @Override
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
