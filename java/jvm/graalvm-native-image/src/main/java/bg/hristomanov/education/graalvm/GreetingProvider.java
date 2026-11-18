package bg.hristomanov.education.graalvm;

/** Малък стабилен contract за сравняване на JVM и native изпълнение. */
public interface GreetingProvider {
    String greet(String name);
}
