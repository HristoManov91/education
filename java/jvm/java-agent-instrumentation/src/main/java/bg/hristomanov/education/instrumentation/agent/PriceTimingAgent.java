package bg.hristomanov.education.instrumentation.agent;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Демонстрира промяна на bytecode ПРЕДИ дефинирането на конкретен клас от JVM.
 * Това е учебен agent с тесен matcher, а не заместител на production OTel agent.
 */
public final class PriceTimingAgent {

    private PriceTimingAgent() {
    }

    /**
     * JVM извиква premain преди main, когато JAR manifest съдържа Premain-Class.
     */
    public static void premain(String agentArguments, Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .type(named("bg.hristomanov.education.instrumentation.sample.PricingService"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(PriceTimingAdvice.class)
                                .on(named("quoteCents").and(takesArguments(2)))))
                .installOn(instrumentation);
    }

    /**
     * Byte Buddy вмъква инструкциите на тези методи в целевия метод.
     * Не извикваме helpers от application classloader, за да няма loader coupling.
     */
    public static final class PriceTimingAdvice {

        private PriceTimingAdvice() {
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        static long onEnter() {
            return System.nanoTime();
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        static void onExit(@Advice.Enter long startedAtNanos, @Advice.Thrown Throwable thrown) {
            long elapsedNanos = System.nanoTime() - startedAtNanos;
            String status = thrown == null ? "OK" : "ERROR";
            System.err.println("LAB_AGENT quoteCents status=" + status + " elapsedNanos=" + elapsedNanos);
        }
    }
}
