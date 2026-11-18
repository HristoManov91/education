# NotebookLM — GraalVM Next: Native Image, Layers, Crema, Web Image

## Източници за зареждане

1. [README — старт на темата](README.md), [Compatibility](NATIVE-IMAGE-COMPATIBILITY.md), [Layers](LAYERS-LAB.md), [Crema](CREMA-EXPERIMENT.md), [Web Image](WEB-IMAGE-RADAR.md), [Exercises](EXERCISES.md).
2. [Alina Yurenko и Thomas Wuerthinger — GraalVM Next, Devoxx 2026](https://www.youtube.com/watch?v=OLQ98Ko4p3Q) + предоставен SRT `GraalVM_Next_Alina_Yurenko_OLQ98Ko4p3Q.srt` (ако е наличен).
3. [Native Image](https://www.graalvm.org/latest/reference-manual/native-image/), [GraalVM Layers](https://graalvm.github.io/native-build-tools/latest/layered-images.html), [Maven plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html), [Tracing Agent](https://www.graalvm.org/latest/reference-manual/native-image/Agent/), [Metadata](https://www.graalvm.org/latest/reference-manual/native-image/metadata/).
4. [GraalVM 25.1](https://www.graalvm.org/release-notes/25.1/), [GraalVM 25.3](https://www.graalvm.org/release-notes/25.3/), [Web Image](https://www.graalvm.org/latest/reference-manual/web-image/).
5. Нашите [Leyden lab](../performance-evolution/README.md), [JVM in Containers](../performance-evolution/JVM-IN-CONTAINERS.md), [Benchmark Correctness](../performance-evolution/BENCHMARK-CORRECTNESS.md), [Brewlet case](../../../docs/case-studies/006-brewlet-java-oci-runtime.md).

## Готов prompt за български аудио урок (35–45 мин)

```text
Създай задълбочен разговорен аудио урок на БЪЛГАРСКИ за Java 25 / Spring Boot backend инженер. Използвай основния README и свързаните документи като учебна последователност, SRT за демонстрациите на Alina Yurenko / Thomas Wuerthinger и официалните GraalVM docs за точните опции, версии и ограничения.

Входен production-like казус: имаме десет Java microservices (общи JDK, Spring/Micronaut и библиотеки), cold startup pressure, неясен RSS memory budget, дълги native builds и динамични Byte Buddy класове. Отвори с причината това да е проблем, а НЕ с GraalVM API.

Постепенно изгради mental model:
1. HotSpot JVM/JIT, GraalVM JIT, GraalVM Native Image AOT и Project Leyden JVM AOT cache — кое остава JVM и кое става native executable.
2. Обясни какво е reachability metadata, reflection, classloaders, dynamic proxy/resources, Native Image tracing agent и защо AI-generated configuration без native regression tests не е доказателство.
3. Обясни и проследи изпълнимия Java25 код RuntimeGreetingApp и GreetingFactory, статичен vs reflective provider, allowlist, checksum и invalid input JUnit tests.
4. Разкажи Native Image Layers: producer java.base / .nil + runtime libbase; два consumer apps; защо това не е Docker layer; как JVM/native memory accounting изисква RSS/PSS/private разграничение.
5. Използвай само като сценични числа base ~200MB, extension ~2MB, PSS ~100MB one app срещу ~30MB per app при 10; изрично поясни, че PSS = private + fractional shared pages, НЕ само private.
6. Обясни какво е Crema (не „Kramer“): runtime class loading за класове, създадени след build, bytecode interpreter и опционален JIT. Разкажи Byte Buddy JVM demo, защо генерирането е различно от metadata за вече съществуващ клас, и защо реален Native Image build трябва да бъде отделен експеримент.
7. Сравни source launcher и Micronaut runtime от лекцията с нашия ограничен Byte Buddy пример; не представяй техните millisecond demo числа като throughput/latency на нашата система.
8. Разкажи Web Image: JS wrapper + Wasm в browser/Node, експериментален статус, защо не е standalone WASI/Wasmtime и не е същото като Brewlet.
9. За JVM, Leyden, Native Image, Layers, Crema и Web Image дай decision contract: какъв проблем решава, кога да/не, ползи, trade-offs, по-проста алтернатива, конкретно доказателство и честен rollback.
10. Практически walkthrough: Maven Java25 JUnit tests; python startup script; AOT cache run; ръчен native build; linux_memory.py; два consumer executable tests; Crema JVM tests.
11. Завърши с 5 въпроса за проверка на разбиране и 1 Senior code review scenario за Spring Boot + Oracle + Kubernetes. Не записвай темата като усвоена автоматично.

Важни граници:
- Root Education CI изпълнява Java 25 тестове, но НЕ прави Native Image/Crema native/Web Image compilation. Не описвай manual experiment като test passed.
- Layers Maven example споделя java.base; НЕ твърди, че компилираме целия Micronaut/Spring stack.
- Не рекламирай ненужна native миграция за long-running backend, ако throughput е ограничен от Oracle или network latency.
- Не използвай „скъси се 10 пъти“ без контролирани benchmark резултати.
- Оригиналната лекция е за GraalVM и Micronaut/Web Image; нашите Java 25 JUnit примери са допълнителна учебна реализация, не сценичен source code.
- При английски technical terms дай кратко обяснение на български при първа употреба, без да превеждаш API identifiers.
```

## След аудиото

Премини през [EXERCISES.md](EXERCISES.md), изпълни реалните JUnit тестове и направи 1–2 повторения на JVM startup измерването. Native експериментите се стартират отделно, когато има подходящ GraalVM toolchain. Резултатите се записват в твоя learning progress **само след действителна проверка на знанията**.
