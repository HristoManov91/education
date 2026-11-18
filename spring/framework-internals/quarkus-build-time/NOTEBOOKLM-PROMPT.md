# NotebookLM — Quarkus Build-time Metaprogramming + Jackson/JIT Regression

## Какви източници да заредя

1. [Основният P2 урок](README.md) и [реалните Java/POM файлове](deployment/src/main/java/bg/hristomanov/education/quarkus/deployment/GreetingBuildProcessor.java).
2. [P1 JVM/Jackson case](../../../java/jvm/performance-evolution/QUARKUS-JACKSON-JIT-REGRESSION.md), [JUnit](../../../java/jvm/performance-evolution/src/test/java/bg/hristomanov/education/performance/quarkus/HandlerDispatchLabTest.java) и [JMH](../../../java/jvm/performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/HandlerDispatchBenchmark.java).
3. [Mario Fusco / Francesco Nigro / Georgios Andrianakis — Devoxx 2026](https://www.youtube.com/watch?v=SgK0zMNayq0), предоставен SRT (до ~02:28, auto-generated) и [оригинален deck](https://speakerdeck.com/mariofusco/the-quarkus-advantage-turning-build-time-metaprogramming-into-runtime-performances).
4. [Quarkus 3.40 extension guide](https://quarkus.io/version/3.40/guides/writing-extensions/), [CDI generated bean](https://quarkus.io/version/3.40/guides/cdi-integration/), [Spring AOT](https://docs.spring.io/spring-framework/reference/core/aot.html), [JEP 416](https://openjdk.org/jeps/416).
5. [Benchmark correctness](../../../java/jvm/performance-evolution/BENCHMARK-CORRECTNESS.md), [Method Handles](../../../java/jvm/method-handles/README.md), [GraalVM Native Image](../../../java/jvm/graalvm-native-image/README.md).

## Готов prompt за българска задълбочена аудио лекция

```text
Направи 35–45-минутна задълбочена разговорна техническа аудио лекция на БЪЛГАРСКИ за Java 25 и Spring Boot backend разработчик. Използвай предоставения SRT и оригиналния deck за изворовия случай; официалната Quarkus 3.40 документация за version-specific API. Обозначавай кога примерът е наш учебен адаптиран код, а не демонстрация от сцената.

Започни с реален проблем: REST app с classpath scanning/reflection при startup, JIT/GC разходи и нужда да обслужва много заявки. Обясни защо „премахвам reflection“ е хипотеза, не performance гаранция.

1. Mental model на Quarkus augmentation, build-time vs runtime (но не смесвай с GraalVM native build).
2. Исторически Reflection до Java 17 срещу JEP 416 Java 18+ с MethodHandles; caller/callee checks; избягвай твърдението „Reflection винаги е скъп“.
3. Jandex: class/annotation index без runtime класово сканиране, CombinedIndexBuildItem, dependent indexing.
4. BuildStep и Simple/MultiBuildItem; топологичен DAG, immutable handoff, потребители и execution order.
5. Gizmo: как генерира CDI bean чрез GeneratedBeanBuildItem и GeneratedBeanGizmoAdaptor; защо runtime трябва да зависи само от interface.
6. Walkthrough на реалната ни Quarkus 3.40.1.2 лаборатория: три Maven модула; GreetingDefinition marker; GreetingBuildProcessor.indexGreeting; GreetingRules; GreetingModelBuildItem; генериран GreetingMessage; инжекция в GreetingResource; QuarkusTest GET.
7. Запиши, че тази Quarkus 3.40 LTS лаборатория използва API, различно от Quarkus 4 main/Gizmo2 в сценичните слайдове. Това е съзнателен version boundary.
8. За всяка техника: проблем, кога да, кога НЕ, печалби, губим, по-проста алтернатива, работещ код и тест. Сравни със Spring AOT, не с карикатура, че Spring няма build-time optimizations.
9. Втори реален казус: Quarkus REST megamorphic call site, instanceof cascade и предварително определен kind + generated tableswitch, unknown fallback. Нашият HandlerDispatchLab е семантичен модел, не копие на Quarkus handler pipeline.
10. Jackson 3: фактическите конференционни числа преди 144019 vs 140073 req/s, след 147115 vs 151154; обясни защо „direct generated serializer“ първо губи.
11. Hot loop inlining, register spilling, String.charAt profile pollution, bounds/table checks и loop unrolling 2x/4x; как се проверяват с профили/JFR/assembly, какво JFR НЕ доказва.
12. JUnit и JMH: какво проверява parity, защо throughput на integer handler lab не доказва JSON/HTTP performance. Подходяща хипотеза, benchmark setup, resource bottlenecks, run variance, rollback и negative tests.
13. Senior code review scenario: Spring Boot + Oracle + Jackson 3, p99 главно DB wait, трябва ли Quarkus? Кога НЕ?
14. Финално: задавай 5 реални въпроса за активно припомняне, но не твърди, че ученето е преминало успешно без отговори.

Не измисляй успешни CI/native тестове, ако липсват данни. Не приписвай Java code от нашия repo на лекторите. Точно разграничи предоставения SRT (~2h28) и пълните 102 слайда. Без неосновани „Quarkus 2x faster“ лозунги.
```

След аудиото: реши [EXERCISES.md](EXERCISES.md), пусни тестовете и провери версията на Quarkus.
