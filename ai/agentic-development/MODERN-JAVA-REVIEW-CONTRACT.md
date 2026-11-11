# P1 — Modern Java review contract за coding agents (Java 25)

> По [Ask the Architects (Devoxx 2026)](https://www.youtube.com/watch?v=ewCPBgOrLAE), ~17:30–28:40 от предоставения SRT. Част от дискусията е за Javadoc, бърз build/test feedback и идиомите на Java, които agents могат да генерират от остарели примери. **Следващият checklist и prompt-like task contract са наша инженерна адаптация**, не формален OpenJDK standard и не буквални инструкции от лекторите.

## 1. Входен backend казус

Coding agent генерира 200 реда Java от стари web snippets. Кодът „работи“ на demo data, но използва legacy date types, manual nullable builders и unsafe thread/resource handling. Искаме **коректен, ясен Java 25 contract**, не автоматично преобразуване на всеки клас в `record` или всяка нишка във Virtual Thread.

### Bad / good

```java
// BAD when we really need an immutable request value, not when mutable entity is intended:
class RequestPair {
    public String id;
    public int quantity;
}

// GOOD for an immutable value with stable components and validated invariants:
record RequestPair(String id, int quantity) {
    RequestPair {
        if (id == null || id.isBlank() || quantity < 1) {
            throw new IllegalArgumentException("Invalid input");
        }
    }
}
```

**Забележка:** `record` не е правилен за mutable JPA entity със самостоятелна identity/lifecycle; `equals/hashCode` включва component values, включително mutable nested references. Това се проверява в [P1 DOP Java25 labs](../../java/language/data-oriented-programming/README.md).

## 2. Task contract, който агентът изпълнява

**Преди промяната**: заяви целевия JDK (`25`), библиотечни версии, API/serialization contracts, concurrency и security boundaries. Идентифицирай какъв проблем решаваме. Когато е възможно, запази independent acceptance oracle, написан **преди** имплементацията.

**Докато пише код**:
- Предпочитай стандартни Java25 API и изчистен, четим код; не копирай автоматично стари patterns (`Thread.stop()`, reflection `setAccessible` без разрешения, стари `Date` конструктори) или preview syntax.
- Използвай records за подходящи immutable value objects, sealed hierarchies само ако екзостивността е цел; не променяй persistence/DTO JSON contracts без миграция.
- `try-with-resources`, explicit boundaries за native/FFM, ownership и cancellation. Virtual Threads НЕ заменят connection pool limits.
- Не добавяй `--add-opens` или `--enable-native-access=ALL-UNNAMED` глобално без ясна причина и scope.
- Не твърди „по-бърз“ без representative JMH/JFR benchmark, отделен от correctness тестовете.

**При review**:
1. **Hard-to-reverse:** public API, security access, schema/serialization, DB transaction, encoding, data lifetime.
2. **Behavior:** edge/boundary/negative tests, async failures, cleanup, null/overflow, race conditions.
3. **Operability:** metrics, logging without secrets, CI, documented rollback.
4. **Style и micro-optimization** — последни, не за сметка на correctness.

Агентът **няма право** да изключва failing tests или да измисля output от непуснат build. При разминаване с upstream specification търсим независим oracle и конкретен fixture.

## 3. Реални proof точки в Education

| Topic | Production-like code | JUnit/CLI |
| --- | --- | --- |
| Data-Oriented Programming/record invariants | [DOP](../../java/language/data-oriented-programming/README.md) | изпълним Java25 модул |
| Detect deprecated code instead of copying it | [Java upgrade lab](../../java/jvm/jdk-upgrade-tooling/README.md) | `jdeprscan` + `javac -Xlint` negative control |
| Minimal JPMS permissions | [JPMS lab](../../java/jvm/jpms-encapsulation/README.md) | `javac` negative + runtime `InaccessibleObjectException` |
| Virtual Threads/resource limits | [Loom decision guide](../../java/concurrency/project-loom/CUSTOM-SCHEDULERS-AND-EVENT-LOOPS.md) | [resource bulkheads JUnit](../../java/concurrency/project-loom/loom-labs/src/test/java/bg/hristomanov/education/loom/labs/limit/ResourceBulkheadsOnVirtualThreadsTest.java) |
| FFM String immutable/ownership | [FFM](../../java/jvm/native-interoperability/STRING-NATIVE-COPY-BOUNDARY.md) | [FFM JUnit](../../java/jvm/native-interoperability/src/test/java/bg/hristomanov/education/ffi/StringCopyBoundaryLabTest.java) |

## 4. Кога и trade-offs

- **Кога:** PR review на agent-generated Java/Spring code, миграция към JDK 25, междусервизни API и performance-sensitive работа.
- **Кога НЕ:** като автоматична забрана на всички legacy interfaces или като unscoped requirement за всяка дребна промяна.
- **Печалба:** по-малко недоказани промени и по-ясни тестови договори; **цена:** CI/ревю време и обучение на екипа.
- **По-просто:** JVM compiler/IDE warnings и кратко code review при малки промени.
- **Доказателство:** посочените *реални* labs и CI. Този документ сам по себе си не гарантира improved model behavior.

**Изход:** агентът помага да имплементираме желаното поведение; Java type system, tests, build, спецификацията и reviewer-ът остават арбитри за правилност.

## Източници

- [Ask the Architects, Devoxx 2026](https://www.youtube.com/watch?v=ewCPBgOrLAE), supplied transcript.
- [Oracle Java Secure Coding Guidelines](https://www.oracle.com/java/technologies/javase/seccodeguide.html).
- [Java 25 migration guide](https://docs.oracle.com/en/java/javase/25/migrate/preparing-migration.html).
- [JEP 395 — Records](https://openjdk.org/jeps/395).
- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444).
