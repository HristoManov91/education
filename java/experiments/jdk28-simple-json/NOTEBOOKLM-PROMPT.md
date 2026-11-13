# NotebookLM · Everyday JSON, JEP540 и JVM diagnostics

**Основен източник:** [Everyday JSON by Viktor Klang and Alan Bateman — Devoxx](https://www.youtube.com/watch?v=5y_82lCevHg), предоставен user SRT (~51 мин). Използвай и [нашия реален JDK28 EA lab](README.md), [Jdk28SimpleJsonLab.java](src/Jdk28SimpleJsonLab.java), [Jdk28JsonThreadDumpLab.java](src/Jdk28JsonThreadDumpLab.java), [Java25/Jackson comparison](../../../spring/messaging/integration-patterns/src/test/java/bg/hristomanov/education/integrationpatterns/json/JsonBoundaryCorrectnessTest.java), [Java concurrency/virtual thread dumps](../../concurrency/project-loom/VIRTUAL-THREADS-LABS.md), [JFR](../../jvm/jfr-troubleshooting/README.md), [JEP540](https://openjdk.org/jeps/540) и [официалните JDK28 EA API docs](https://download.java.net/java/early_access/jdk28/docs/api/jdk.incubator.json/jdk/incubator/json/package-summary.html).

Създай **30–40 минути практически и подробен български аудио урок за Mid+/Senior Java backend developer**. Разграничавай какво *реално твърдят лекторите*, какво е официален JEP540/JDK28 EA behavior и какво е собствено Education тестово решение:

1. Everyday JSON в конфигурации, HTTP payload-и, CLI, tools и JDK diagnostics. Защо не е нужно всеки малък CLI да ползва Jackson; защо това НЕ е призив да сменяме Jackson в Spring Boot.
2. Въвеждане на JEP540: incubating `jdk.incubator.json` в JDK28 EA, шест JSON типа, sealed `JsonValue`, immutable tree; `--add-modules` при компилация/изпълнение. **НЕ го описвай като Java25 final feature**.
3. Малък JSON config пример: required `get`, optional `tryGet`, missing vs explicit `JsonNull`, `tryValue`, defaults. Обясни разликата от Jackson `NullNode/MissingNode`.
4. Защо strict RFC8259 parsing е важно: duplicate keys, comments, trailing commas, invalid escapes, parser differentials между independent services. Покажи ролята на Jackson 3 `StreamReadFeature.STRICT_DUPLICATE_DETECTION` (не се предполага от default).
5. Числова коректност: exact `asInt/asLong`, lossy `asDouble`, `BigDecimal/BigInteger` от `JsonNumber.toString()`. Пример `2^53+1` и сума с много десетични знаци. JSON tree валидност ≠ бизнес правило.
6. Alan Bateman: `jfr print --json`, `jcmd PID Thread.dump_to_file -format=json`, вложени threadContainers/threads. Нашият JDK28 EA lab прави **истински**, не mocked virtual thread dump; защо `virtual` е optional и защо JDK27+ `processId/tid` са JSON numbers.
7. Обсъди `Properties`↔JSON, field-order preservation vs business order contract, прочитането на string-vs-number legacy ID с pattern matching, immutability, record mapping като **неподдържан автоматичен feature**.
8. За всяка технология: **какъв проблем, кога, кога НЕ, печалби/загуби, по-просто решение, работещ пример и тест**. Ако имаш production Spring, запази Jackson. Не измисляй performance числа и не давай неинсталиран JDK28 syntax.

Завърши с **10 Senior-level въпроса без готови отговори** и кратък migration checklist за евентуална бъдеща GA версия, ако API достигне такава. Не измисляй release date и не обявявай JEP540 като стабилна Java28 feature.
