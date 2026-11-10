# P2 — JDK 27 C2 SuperWord: кога обикновен Java loop става SIMD

> Изходен проблем от [Per-Åke Minborg — Yearly Java Performance Update 2026](https://www.youtube.com/watch?v=rNOzf12RORI), ~25–28 мин., предоставен SRT. Сверено с [JDK 27 Performance Improvements](https://inside.java/2026/09/28/performance-update-jdk27/) и JDK-8342095/JDK-8370863. Основният Java25 lab не използва preview/incubator APIs.

## 1. Реален казус

Четем binary payload или сензорни стойности от `short[]` и ги преобразуваме в `int[]`, или сериализираме числа към `byte[]`. Обикновен цикъл:

```java
for (int i = 0; i < source.length; i++) {
    result[i] = (byte) source[i];
}
```

Наивно е да твърдим „пусни `jdk.incubator.vector` и винаги става по-бързо“. C2 компилаторът има **SuperWord auto-vectorization** (разпознаване на подходящи цикли и преобразуването им в SIMD), но има цена за vector setup, alignment и обработка на оставащите елементи. JDK 27 добавя по-добро разпознаване на `byte`/`short` cast patterns, без да променя source API.

## 2. Какво точно се подобрява

- **JDK-8342095:** SuperWord обработва по-добре narrow/widen conversions на subword типове в цикли.
- **JDK-8370863:** C2 премахва излишни vector-mask cast вериги и разпознава някои load/cast/store операции без реална промяна на стойността.
- Това **не означава**, че всеки loop е векторизиран на всеки CPU. ISA/SVE/AVX, data shape и compiled code решават.
- **Vector API** е отделен explicit модел с incubator status, покрит вече в [основния Vector API модул](README.md). Не го дублираме тук.

## 3. Код и доказателство (README → code)

| Концепция | Java 25 код | Доказателство |
| --- | --- | --- |
| `int -> byte` narrowing и `short -> int` signed widening | [SubwordLoops.java](src/main/java/bg/hristomanov/education/vector/SubwordLoops.java) | [SubwordLoopsTest.java](src/test/java/bg/hristomanov/education/vector/SubwordLoopsTest.java) — lengths 0, 1, 15/16/17, 63/64/65, negative/sign boundaries |
| C2 optimization experiment | [SubwordLoopsBenchmark.java](../performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/SubwordLoopsBenchmark.java) | JMH на JDK25 и JDK27 при същия JAR, `-prof gc` |

```bash
mvn -pl java/jvm/vector-api -am test
mvn -pl java/jvm/performance-evolution-benchmarks -am -DskipTests package

# С еднакви JVM flags/CPU и на двете версии:
"$JDK25_HOME/bin/java" -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  SubwordLoopsBenchmark -wi 3 -i 5 -f 2 -prof gc

"$JDK27_HOME/bin/java" -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  SubwordLoopsBenchmark -wi 3 -i 5 -f 2 -prof gc
```

Параметрите 16/65/4096 разкриват различна цена на vector setup и tail. Не сравнявай `narrowInts` срещу `widenShorts` като еднаква бизнес операция; сравнявай **една и съща операция** между JDK версиите. JMH може да показва time/GC allocation, но **не доказва генериран SIMD**. За това трябва compiler diagnostics/assembly или hardware counters на реален CPU, без да смесваме interpreter и JIT phases.

## 4. Decision contract

| Подход | Кога | Кога НЕ | Печалба | Цена | По-проста алтернатива |
| --- | --- | --- | --- | --- | --- |
| Обикновен Java loop + C2 | типично преобразуване на primitive arrays, профилиран hotspot | когато няма CPU bottleneck или loop има сложни dependencies | maintainable Java код и възможен SIMD без API dependency | неуправляемо JIT решение, ISA вариабилност | остави първо очевидния loop |
| Explicit Vector API | доказано heavy numerical kernel, нужни masks/lanes | за малки масиви без доказан gain или когато incubator не е приемлив | контрол над векторната стратегия | preview/incubator/version compatibility и tail code | scalar/auto-vectorized code |
| Native SIMD kernel | hotspot извън потенциала на HotSpot, стабилен ABI | без profiling или cross-platform maintenance | потенциално по-бърз specialized kernel | native deploy/security/FFM overhead | Vector API или scalar |

**Bad** е да заменим цикъла с unsafe int/byte cast или да игнорираме signedness само за да се „векторизира“. **Good** е да запазим числения contract, да проверим tail и overflow/signedness и след това да измерим.

## 5. Изход / review

От началния казус: няма нужда да пренаписваме backend code с Vector API само защото JDK 27 има нови SIMD оптимизации. Java25 unit тестовете пазят резултата; JDK25/27 JMH сравнява скоростта. Проверяваме CPU и дължините на масивите, не чужд percent.

Checklist: [ ] input size distribution; [ ] signed vs unsigned contract; [ ] tail/empty tests; [ ] същата операция при двете JDK версии; [ ] hardware ISA; [ ] няма твърдение „SIMD“, базирано само на time.

## Оригинални източници

- [Per-Åke Minborg — Devoxx 2026](https://www.youtube.com/watch?v=rNOzf12RORI).
- [JDK 27 Performance Improvements](https://inside.java/2026/09/28/performance-update-jdk27/).
- [JDK-8342095 — subword casts](https://bugs.openjdk.org/browse/JDK-8342095).
- [JDK-8370863 — vector cast cleanup](https://bugs.openjdk.org/browse/JDK-8370863).
- [JEP 537 — Vector API, Twelfth Incubator](https://openjdk.org/jeps/537).
