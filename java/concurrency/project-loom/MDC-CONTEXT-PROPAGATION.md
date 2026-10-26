# Focused Lab: MDC/ThreadLocal → Virtual Threads (Java 25)

> **Source:** [Modern Java in Practice — Scoped Values/MDC segment, ~1:25–1:50](https://youtu.be/zC2tbq7S5UE?t=5100).  
> Това **надгражда** съществуващия [ScopedValue vs ThreadLocal lesson](VIRTUAL-THREADS-LABS.md); **не го дублира**.

## Реалният случай

Spring request има correlation ID за logs/trace, държан в framework MDC, базиран на ThreadLocal. Request handler стартира няколко virtual threads:

```text
Request thread: MDC[requestId] = "req-42"
  └─ plain virtual Thread → MDC[requestId] = null
```

**Мит:** virtual thread автоматично наследява родителския ThreadLocal. Не — обикновеният ThreadLocal е привързан към конкретната нишка. `InheritableThreadLocal` може да копира state при създаване на child, но mutable context/inheritance, множество virtual threads и memory cost могат да станат проблем.

## BAD — assume context propagates magically

```java
ThreadLocal<String> requestId = new ThreadLocal<>();
requestId.set("req-42");
Thread child = Thread.ofVirtual().start(() -> System.out.println(requestId.get()));
child.join(); // prints null for ordinary ThreadLocal
```

При thread pools има втори проблем: ако ThreadLocal не се изчисти, следващ unrelated task може да види чужд request context. **Cleanup в finally** е задължителен, ако използваш традиционен logging MDC.

## GOOD — prefer ScopedValue in newly designed structured code

[ScopedValueRequestContext](loom-labs/src/main/java/bg/hristomanov/education/loom/labs/context/good/ScopedValueRequestContext.java) вече ни показва:
- immutable-ish scoped binding;
- automatic cleanup when lexical scope ends;
- inheritance into structured child task tree;
- ясни lifetime constraints.

Не използвай ScopedValue като mutable global registry; контекстът трябва да е read-only/scope-bound.

## Legacy integration: explicit ThreadFactory snapshot bridge

[ThreadLocalContextBridge.java](loom-labs/src/main/java/bg/hristomanov/education/loom/labs/context/ThreadLocalContextBridge.java) демонстрира **реална виртуална нишка**, която:
1. прави snapshot на parent context **при ThreadFactory.newThread(task)** (не при factory construction);
2. задава копието в child;
3. изпълнява task;
4. в `finally` възстановява/изтрива child context дори при exception;
5. запазва parent context независимо от child modifications.

Тази учебна реализация използва собствен `ThreadLocal<Map<String,String>>`, за да не добавяме logging framework към pure-Java Loom lab. **Не е SLF4J MDC adapter**. В Spring Boot/Logback реалната аналогия е:

```java
Map<String, String> captured = MDC.getCopyOfContextMap();
return baseThreadFactory.newThread(() -> {
    try {
        if (captured != null) MDC.setContextMap(captured);
        else MDC.clear();
        task.run();
    } finally {
        MDC.clear(); // or restore previous context if thread reuse requires it
    }
});
```

В logging integration трябва изрично да се определи whether to capture context at scheduling vs execution time, tracing/span lifetime, privacy constraints, nested context restore, mutable map copies и cleanup. Не използвай blindly наследяването на MDC в широк thread pool.

**Test:** [ThreadLocalContextBridgeTest.java](loom-labs/src/test/java/bg/hristomanov/education/loom/labs/context/ThreadLocalContextBridgeTest.java) доказва non-inheritance, per-task capture, cleanup, parent isolation.

```bash
mvn -pl java/concurrency/project-loom/loom-labs -am test
```

## Senior review questions

- Защо ThreadLocal в Request thread не е автоматично достъпен в virtual child?
- Какво означава inheritance на **mutable** MDC context?
- Защо е важно capture at task submission, а не веднъж при startup?
- Какво става при nested tasks и exception, ако липсва finally cleanup?
- Кога ще избереш ScopedValue, кога ThreadFactory propagation, кога OpenTelemetry Context?
- Може ли virtual thread да наследява голям Map 100 000 пъти без resource implications?

**Изход:** ScopedValue по подразбиране за собствен scoped read-only контекст; ThreadLocal/MDC propagation като **явен bridge** за legacy integrations, с test и cleanup.

## Източници

- [Devoxx 2026](https://youtu.be/zC2tbq7S5UE)
- [JEP 506 — Scoped Values](https://openjdk.org/jeps/506)
- [JEP 505 — Structured Concurrency JDK 25](https://openjdk.org/jeps/505)
- [Java 25 Thread API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html)
