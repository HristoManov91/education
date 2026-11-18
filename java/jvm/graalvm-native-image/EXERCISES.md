# P2 — GraalVM Native Image, Layers и Crema: активни упражнения

> Тези задачи **не означават автоматично усвоено знание**. Изпълни и обясни резултатите, преди да актуализираш progress. [Старт: основният урок](README.md).

## Сесия A — Java 25 correctness (20–30 мин)

1. Пусни `mvn -pl java/jvm/graalvm-native-image -am test`. Защо два различни code path-а (static и reflection) трябва да връщат един и същ checksum?
2. Какво става, ако зададеш `--iterations=0` или непознат argument? Защо не позволяваме произволно име на class provider?
3. Отвори [reachability-metadata.json](src/main/resources/META-INF/native-image/bg.hristomanov.education/graalvm-native-image/reachability-metadata.json): кой constructor се регистрира? Това дали разрешава генериране на нов bytecode след native build?

## Сесия B — JVM/JIT/Leyden/Native Image

4. Стартирай `scripts/measure_startup.py` за стандартния Java 25 runtime и за същия workload с `-XX:AOTCache`. Как ще докажеш, че cache е съвместим и използван?
5. Защо startup-to-exit на малък процес **не** е throughput и защо sum RSS при много процеси надценява действително използваната памет?
6. При 10 Spring приложения кой би бил по-важният съвместим benchmark: main() time, readyness, steady-state QPS, p99 latency или deployment memory? Защо?

## Сесия C — Layers + Crema

7. Отвори [base-layer/pom.xml](layers-lab/base-layer/pom.xml) и двата потребителя. Кои artifacts са build-only и кои runtime? Какво ще се случи, ако runtime shared library липсва?
8. Ако при един процес RSS=150 MB, PSS=100 MB, а при 10 PSS per process=30 MB, означава ли това 30 MB изцяло частна памет? Обясни private pages срещу shared pages.
9. Пусни JVM тестовете от [CremaDemoTest.java](crema-lab/src/test/java/bg/hristomanov/education/graalvm/crema/CremaDemoTest.java). Защо имената на генерираните класове се различават? Защо предефиниране на `ReflectionGreeting` в metadata не решава същия проблем?

## Senior checkpoint — production decision

Микросървис има Spring Boot + Oracle + Redis, Java 25, бавен първи request, но след 10 минути steady-state p99 зависи основно от DB. Предложени са: Leyden, standalone Native Image, Layers, Crema.

Направи решение за **всяка** опция с: проблема, кога да/не, полза, цена, по-проста алтернатива, тест/инструмент и rollback. Без доказани метрики не посочвай „Native е по-бърз с X%“.

## Практическо затваряне

- [README](README.md) за mental model и decision table.
- [Compatibility lab](NATIVE-IMAGE-COMPATIBILITY.md) за metadata vs dynamic bytecode.
- [Layer experiment](LAYERS-LAB.md) с Linux PSS script.
- [Crema experiment](CREMA-EXPERIMENT.md) с реални Java tests.
- [Web Image P3](WEB-IMAGE-RADAR.md) — няма измислен Wasm build.
