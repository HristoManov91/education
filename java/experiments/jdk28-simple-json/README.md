# P1/P2 · Everyday JSON — JEP 540 (real JDK 28 EA) и строг JSON contract

> **Практическа лекция от [Viktor Klang и Alan Bateman — Everyday JSON, Devoxx Belgium 2026](https://www.youtube.com/watch?v=5y_82lCevHg)**, предоставен автоматичен SRT (~51 мин). Нашият код е **нов собствен Education lab**, не копие на демонстрациите от лекцията.  
> **Version boundary:** \`jdk.incubator.json\` е incubating API в **JDK 28 EA**, **НЕ** е Java 25/27 final API. Java 25 + Spring Boot/Jackson 3 остава production baseline; **няма нов Maven module**.

## 1. Реалният проблем: прекалено много зависимости за малък JSON скрипт

За кратък CLI искаме да прочетем конфигурация, да извлечем поле от HTTP JSON response или да анализираме JVM thread dump. Обичайното решение е Jackson/Gson/JSON-P, но когато ни трябва единствено **JSON value tree**, голям framework изглежда непропорционален. JEP 540 предлага малък, **immutable** tree API за шестте JSON типа.

**Това НЕ е „Java заменя Jackson“:** няма general-purpose Java record/POJO mapping, custom serializers, streaming JSON parser за GB потоци, Schema validation, Spring MVC binding и ecosystem modules. Framework сериализацията остава отделна грижа.

| Подход | Кога | Кога НЕ | Печалба | Цена и алтернатива |
| --- | --- | --- | --- | --- |
| JEP 540 \`Json\`/ \`JsonValue\` | JDK28 EA CLI, JShell, малък JSON input/output и diagnostika | Java25 production и сложна интеграция | JDK-only прости операции, sealed tree, immutability | EA churn, opt-in incubator; **Jackson/JSON-P** за production |
| Jackson 3 \`JsonMapper\` | Spring Boot, DTO records, polymorphic binding, конфигурируем parser | Малък JDK28 CLI без нужда от dependencies | mature ecosystem & schema frameworks | dependency + configuration/attack surface; configure strict mode |
| Java IO + custom parsing | **Обикновено не го използваме** | JSON със string escaping/nesting/numbers | няма реална полза за standards compliance | непредвидими security/parser бъгове |

## 2. Какви точно идеи извлякохме от лекцията

| Приблизително време в предоставения SRT | Тема | Наше доказателство |
| --- | --- | --- |
| 00–09 мин. | Simple JSON за daily work, JEP 540 vs JEP 198 | isolated JDK28 EA lab, без root Maven промени |
| 09–21 мин. | \`Json.parse\`, \`get\`, \`tryGet\`, JSON HTTP/config, string/number migration | [Jdk28SimpleJsonLab](src/Jdk28SimpleJsonLab.java) |
| 21–28 мин. | RFC8259 strictness, duplicate member names, comments, trailing commas | реални negative tests, \`JsonParseException\` |
| 28–39 мин. | Alan Bateman: JFR print JSON, JSON thread dumps, optional virtual property, \`Properties\`↔JSON | [real \`jcmd\` JSON dump lab](src/Jdk28JsonThreadDumpLab.java) |
| 40–51 мин. | incubator scope, immutability, числова точност, ordering и record/object mapping | \`BigDecimal\` exactness tests и decision matrix |

**Не дублираме вече наличните Loom/JFR курсове**; новото е реалното им четене през JEP540. \`Properties\`↔JSON e обсъждана посока от лекцията — тук не изграждаме generic config converter. JMX JSON / HTTP auto-binding / arbitrary Java POJO mapping остават предложения/желания, а не delivered JEP 540 APIs.

## 3. README → source → реални изпълними проверки

| Сценарий | Код | Какво доказва |
| --- | --- | --- |
| Parse/config и optional fields | [Jdk28SimpleJsonLab](src/Jdk28SimpleJsonLab.java) | required vs optional, defaults, \`get\` fails for missing |
| **Missing ≠ explicit JSON null** | Същият executable | \`tryGet("missing")\` empty; \`tryGet("null")\` present, \`tryValue()\` empty |
| Duplicate member names, malformed escape/comments/trailing commas | Същият executable | \`JsonParseException\`, вкл. nested duplicates |
| Exact int/long, BigDecimal/BigInteger, lossy double | Същият executable | bounded conversions vs arbitrary precision, \`JsonValueException\` |
| Immutable \`JsonObject\`/arrays | Същият executable | mutation throws \`UnsupportedOperationException\` |
| Real JVM JSON thread dump from \`jcmd\` | [Jdk28JsonThreadDumpLab](src/Jdk28JsonThreadDumpLab.java) | numeric PID, nested threadContainers/threads, optional \`virtual\`, named **real virtual thread** |
| **Java25 Jackson 3 comparison** | [JsonBoundaryCorrectnessTest](../../../spring/messaging/integration-patterns/src/test/java/bg/hristomanov/education/integrationpatterns/json/JsonBoundaryCorrectnessTest.java) | \`StreamReadFeature.STRICT_DUPLICATE_DETECTION\`, opted-in lax syntax, exact money and mapping |
| Reproducible execution | [run-lab.sh](run-lab.sh) | actual JDK28 \`javac\`, \`java --add-modules\`, **no mocks/skips** |
| Separate GitHub CI | [jdk28-simple-json.yml](../../../.github/workflows/jdk28-simple-json.yml) | real official OpenJDK28 EA (same resolver as Valhalla) |
| Български аудио урок | [NOTEBOOKLM-PROMPT.md](NOTEBOOKLM-PROMPT.md) | source-backed teaching guide |

### Как да стартираш

Изискване: официален **JDK 28 Early Access**, чието \`java --list-modules\` показва \`jdk.incubator.json\`. Нужни са \`java\`, \`javac\`, \`jcmd\` и Linux/macOS или WSL с bash. От корена на Education:

~~~bash
# JDK 28 EA в PATH:
java --version
java --list-modules | grep '^jdk.incubator.json@'
bash java/experiments/jdk28-simple-json/run-lab.sh

# Отделно, с Java 25 (main Maven reactor):
mvn -pl spring/messaging/integration-patterns \
  -Dtest=JsonBoundaryCorrectnessTest test

# Не добавяй incubating JDK28 Java sources към root Maven compile!
~~~

Скриптът fail-ва, ако JDK версията е друга, ако JEP540 модулът липсва, ако компилацията/тестовете се провалят или ако реалният dump не съдържа именувания virtual thread. CI не изисква собствени credentials, Docker, network service, реален JFR запис или Maven на JDK28.

## 4. Най-важният correctness урок: не бъркай валиден JSON с валиден бизнес вход

**Два \`role\` member-а** в един и същ JSON обект могат да бъдат обработени различно от различни parsers, което води до parser differential/security риск. JEP540 **отхвърля** duplicate names, comments и trailing commas. RFC8259 казва, че имената в обект SHOULD да са уникални; това **не налага** всеки JSON parser да има един и същ default. За Jackson 3 \`STRICT_DUPLICATE_DETECTION\` се **включва изрично**, затова [нашият Java25 тест](../../../spring/messaging/integration-patterns/src/test/java/bg/hristomanov/education/integrationpatterns/json/JsonBoundaryCorrectnessTest.java) показва и relaxed опции.

| Input | JEP 540 (JDK28) | Jackson 3 (Education Java25) |
| --- | --- | --- |
| \`{"role":"USER","role":"ADMIN"}\` | exception | Explicit \`STRICT_DUPLICATE_DETECTION\` **трябва да е включен**, иначе default може да използва последната стойност |
| \`{"a":1,}\` / comment | exception | strict default reject; explicit \`JsonReadFeature\` opt-in може да го приеме |
| \`{"x":null}\` vs \`{}\` | explicit \`JsonNull\` vs absent | \`NullNode\` vs \`MissingNode\` |
| \`1.25\` → int | \`JsonValueException\` | **не** използвай silent coercing \`asInt\` като validation |
| High precision JSON number | \`new BigDecimal(number.toString())\` | Jackson decimal tree/typed BigDecimal вместо double roundtrip |
| JSON tree → Java record | **няма built-in mapping** | Jackson \`readValue(..., Record.class)\` |

**JEP540 \`asInt\` и \`asLong\`** искат *точно* преобразуване; \`asDouble\` гарантира конечен double при допустим вход, **не произволна десетична точност**. За финансови стойности използвай \`BigDecimal\` и съответната бизнес валидация. **JSON parse успех ≠ JSON Schema validation/authorization**.

## 5. Real JVM diagnostics — важен production нюанс

\`jcmd PID Thread.dump_to_file -format=json /tmp/threads.json\` и \`jfr print --json recording.jfr\` **съществуват независимо от JEP540**. Alan демонстрира JSON output за JVM diagnostics; нашият EA lab ги свързва, като създава real virtual thread, прави \`jcmd\` dump на собствения процес и парсва през \`Json.parse\`.

В **JDK 27** thread dump идентификаторите се променят от JSON strings към **JSON numbers** (не допускай parser, който hard-code-ва \`tid.asString()\`). [JDK27 compatibility note](https://inside.java/2026/05/20/quality-heads-up/) / [официален формат](https://docs.oracle.com/en/java/javase/27/docs/api/jdk.management/com/sun/management/doc-files/threadDump.html).

**Сигурност и ограничения:** thread dump може да съдържа чувствителни stack/lock/thread-name данни. В тази лаборатория dump-ът е само временен файл и се премахва след проверката; не го качвай към публичен CI artifact. \`jcmd\` attach се прави към **собствения** тестов процес, не към чужд production PID. Форматът на JVM diagnostics може да се развива. Наличието на поне един live thread в един dump не е доказателство за performance/lock behavior.

## 6. Граници на новия API

- \`JsonValue\` е **sealed**, стойностите са immutable и thread-safe; object member iteration е в input encounter order за \`Json.parse\`, но **редът на полетата няма да бъде наш бизнес договор**.
- \`JsonNumber.toString()\` е подходящ за arbitrary precision parsing, но не предполагай, че lexical representation винаги е същата като оригиналния JSON string.
- \`Json.parse\` работи със \`String\`/ \`char[]\` и построява tree; **не е** streaming reader за unbounded requests. За неоверени JSON boundary следват ограничения на размер, depth, authentication и schema validation, които се налагат **извън** този API.
- В Java25 production се използва нашият existing Jackson 3 stack с explicit parser и DTO policies. Няма обещание, че бъдещ JDK ще замени Jackson.

## 7. Decision contract

**Проблем:** обработка на малък JSON без ненужен framework overhead, съчетана с коректна parser policy.

**Кога:** JDK28 EA experiments, CLI, JShell, JVM JSON diagnostics, еднократни/простички tools.

**Кога НЕ:** Java 25 production, сложен Spring Boot object binding, streaming multi-GB files, security-sensitive endpoints без лимити и проверка.

**Печалби:** малък API, no extra JSON dependency в JDK28 environment, explicit null/missing, strict syntax и точни numeric conversions.

**Загуби:** early-access + incubator migration risk, API feature limitations и нов test matrix.

**Алтернатива:** Jackson/JSON-P за production; за 1–2 полета \`JsonMapper.readTree\` вече работи в съществуващия Spring dependency graph.

**Proof:** двата независими lab-а и GitHub workflows; не измисляме throughput measurements или гаранции за окончателния JDK28 API.

## Оригинални и официални източници

- [Everyday JSON — Viktor Klang / Alan Bateman](https://www.youtube.com/watch?v=5y_82lCevHg), предоставен SRT (0–51 min).
- [JEP 540 — Simple JSON API (Incubator)](https://openjdk.org/jeps/540).
- [Официални JDK28 EA \`jdk.incubator.json\` docs](https://download.java.net/java/early_access/jdk28/docs/api/jdk.incubator.json/jdk/incubator/json/package-summary.html).
- [JDK28 EA downloads](https://jdk.java.net/28/).
- [RFC 8259 JSON](https://www.rfc-editor.org/rfc/rfc8259).
- [OpenJDK Quality Outreach — JDK27 numeric thread dump IDs](https://inside.java/2026/05/20/quality-heads-up/).
