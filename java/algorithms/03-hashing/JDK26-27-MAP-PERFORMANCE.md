# P2 — JDK 26/27: record hashCode и HashMap bulk copying

> По [Per-Åke Minborg — Yearly Java Performance Update 2026](https://www.youtube.com/watch?v=rNOzf12RORI), ~22–25 мин (предоставен SRT). Фактите за implementation версията са сверени с [JDK 26](https://inside.java/2026/06/09/jdk-26-performance-improvements/) и [JDK 27](https://inside.java/2026/09/28/performance-update-jdk27/). Java25 runnable functional tests; JMH се намира в **съществуващия** [benchmark module](../../jvm/performance-evolution-benchmarks/README.md).

## 1. Защо ни засяга

В backend имаме `Map<CompositeKey, Product>` с ключ `record CompositeKey(...)`. Копираме map-а при изграждане на snapshot за HTTP response или cache. Кодът изглежда безобиден:

```java
HashMap<DefaultKey, Integer> snapshot = new HashMap<>(source);
```

Под капака старата реализация на `HashMap(Map)` и `putAll` минава през `Map` интерфейса: iterator, virtual calls, извличане на key/value и извикване на `hashCode()`. **Това не е нулева цена**, особено при polymorphic source types и unmodifiable wrappers.

JDK 27 (JDK-8371656) има специален път при source точно `HashMap` или unmodifiable wrapper върху такъв: обхожда съществуващите nodes и може да **използва кеширания hash**, вместо отново да го смята. За други `Map` реализации продължава общата стратегия. **Не** е универсално ускорение на всяко `putAll`.

В JDK 26 (JDK-8366424) по-добрият type profiling на генерираните `record.hashCode()` методи помага на HotSpot JIT да ги inline-ва. Това не променя публичния contract на `hashCode`.

## 2. README → код → доказателство

| Тема | Код | Контрол / benchmark |
| --- | --- | --- |
| Вход: `Map`, `HashMap`, `Collections.unmodifiableMap` | [MapCopyLab.java](src/main/java/bg/hristomanov/education/algorithms/hashing/model/MapCopyLab.java) | [MapCopyLabTest.java](src/test/java/bg/hristomanov/education/algorithms/hashing/model/MapCopyLabTest.java) |
| Constructor copy срещу `putAll`, конкретен source type | [JMH MapCopyAndRecordHashBenchmark.java](../../jvm/performance-evolution-benchmarks/src/main/java/bg/hristomanov/education/performance/benchmarks/MapCopyAndRecordHashBenchmark.java) | functional proof по-горе + JMH (не JUnit performance assertion) |
| Default record hashCode срещу ръчен | [DefaultKey / ManualHashKey](src/main/java/bg/hristomanov/education/algorithms/hashing/model/MapCopyLab.java) | същият JMH, два различни record типа |

```bash
# От education root: Java25 функционално поведение
mvn -pl java/algorithms/03-hashing -am test

# JMH JAR (ползва съществуващия harness):
mvn -pl java/jvm/performance-evolution-benchmarks -am -DskipTests package

java -jar java/jvm/performance-evolution-benchmarks/target/benchmarks.jar \
  MapCopyAndRecordHashBenchmark -wi 3 -i 5 -f 2 -prof gc
```

Пусни **същия JAR** и на JDK 27 (`$JDK27_HOME/bin/java -jar ...`), запази CPU/OS/GC/JDK vendor, JVM flags и суровите резултати. Първо гледай `mapConstructor` и `mapPutAll` за `hashmap`, `unmodifiableHashMap`, `linkedHashMap`, по размери 5/150/2048. Не свързвай ускорение на `HashMap` със същото за `LinkedHashMap`. За record hashing отдели същите по размери `defaultRecordHash` и `manualRecordHash`.

**Важно:** default record hashCode няма задължение да връща идентично число с ръчното `31 * ... + ...`. Важният contract е `equals(a,b) ⇒ hashCode(a)==hashCode(b)` **за един и същ тип**, а не между два различни типа. JIT оптимизацията не е причина да override-ваме `hashCode` за всеки record.

## 3. Bad / Good и trade-offs

**Bad, но изкушаващо:** пренаписваме всеки record `hashCode`, копираме `Map` с custom цикъл, защото лекторът показва голям процент. Това добавя риск от нарушение на `equals/hashCode` и dependencies върху детайли на конкретна JDK версия.

**Good:** оставяме ясните `new HashMap<>(source)` или `putAll`, upgrade-ваме JVM със стабилни tests; оптимизираме application code само ако profiling на реалния workload покаже hotspot.

| Подход | Кога да | Кога НЕ | Печалби | Загуби | По-просто | Proof |
| --- | --- | --- | --- | --- | --- | --- |
| JDK27 optimized copy | съществуващ HashMap copy hotspot | копиране рядко или source не е HashMap-backed | по-малко indirection/hash calls за допустимия shape | upgrade compatibility, няма гарантиран gain | остави стандартния constructor | [JUnit](src/test/java/bg/hristomanov/education/algorithms/hashing/model/MapCopyLabTest.java) + JMH |
| Record default hash | immutable business key | невнимателно mutable nested fields | по-малко custom code и шанс за JIT improvements | рискове при неправилен key design | не override-вай без причина | equality/copy test + JMH |

**Negative case:** `Collections.unmodifiableMap(hashMap)` е **view**, което вижда бъдещи структурни промени на source. `new HashMap<>(view)` е **shallow copy**, не клонира mutable values. `Map.copyOf(source)` е отделен contract с null restrictions. Не замествай едното механично с другото.

## 4. Какво решихме

Преди приемахме, че всеки `HashMap` copy и всеки record hash са еднакви при всички JDK версии. Сега различаваме concrete source, call-site polymorphism, JIT profile и различните бизнес договори. JUnit пази correctness; JMH е само **измерване на нашата машина**. Не твърдим общо application ускорение.

**Review checklist:** [ ] concrete source type; [ ] null/keys/value contract; [ ] mutable keys?; [ ] shallow vs deep copy; [ ] equality/hash consistency; [ ] JDK/CPU/warmup; [ ] negative source controls; [ ] реален bottleneck ли е.

## Оригинални източници

- [Оригиналната лекция](https://www.youtube.com/watch?v=rNOzf12RORI) (~22–25 мин; предоставен SRT).
- [JDK-8366424 — generated record methods](https://bugs.openjdk.org/browse/JDK-8366424).
- [JDK-8371656 — faster HashMap copying](https://bugs.openjdk.org/browse/JDK-8371656).
- [Inside Java JDK 26](https://inside.java/2026/06/09/jdk-26-performance-improvements/), [Inside Java JDK 27](https://inside.java/2026/09/28/performance-update-jdk27/).
- [JMH](https://github.com/openjdk/jmh), [съществуващ benchmark методология](../../jvm/performance-evolution/BENCHMARK-CORRECTNESS.md).
