# P1 · IntelliJ IDEA Debugger — Stream Debugger, Logpoints, Evaluate и AI agent guardrails

> Devoxx 2026 [Learning modern Java the playful way, 49:45–55:20](https://youtu.be/QBQ11aL2aZc?t=2985) — Marit van Dijk / Piotr Przybył. **Инструментален walkthrough, не нов JVM модул и не автоматизиран JUnit тест.** За production JFR и регресионни тестове виж [JFR](README.md).

## 1. Реален проблем

При bug в Java Stream намираме грешния елемент едва в края на pipeline. Добавяме `peek(System.out::println)`, променяме код, пускаме нова версия, забравяме debug реда. Ако бъгът е в dependency JAR, дори не можем лесно да добавим `println`.

Цел: **наблюдаваме работещото приложение, без да редактираме business logic**, и превръщаме откритото поведение в постоянен тест. Това не дава право да отваряме remote production debugger без одобрение.

## 2. Практическа задача в IntelliJ

Използвай вече готовите [StreamGatheringExercises.java](../../streams/stream-gatherers/src/main/java/bg/hristomanov/education/gatherers/StreamGatheringExercises.java) и [JUnit](../../streams/stream-gatherers/src/test/java/bg/hristomanov/education/gatherers/StreamGatheringExercisesTest.java).

1. Стартирай `StreamGatheringExercisesTest#slidingWindowsOverlapAndHandleSmallerInput` в Debug mode.
2. Добави обикновен breakpoint върху pipeline-а в [`slidingWindows`](../../streams/stream-gatherers/src/main/java/bg/hristomanov/education/gatherers/StreamGatheringExercises.java) и използвай **Trace Current Stream Chain** (Java Stream Debugger; наличността на UI варира по IntelliJ версия/плъгин). Сравни input `1,2,3,4`, `windowSliding(3)` и final output.
3. Отвори breakpoint settings и изключи **Suspend**; включи **Evaluate and log** / message и укажи безопасен израз, например `input.size()`. Това е **logpoint**, който пише без да спира thread-а.
4. Използвай **Evaluate Expression** върху immutable локално копие или странично-ефектно свободна функция. Не оценявай `save()`, `delete()`, `commit()` и подобни — Evaluate изпълнява **истински код** в debugged JVM.
5. Премахни logpoint-а и провери, че source diff е празен; добави постоянен JUnit regression test само ако е намерен реален пропуск.

**Карта README → код:** [StreamGatheringExercises.java](../../streams/stream-gatherers/src/main/java/bg/hristomanov/education/gatherers/StreamGatheringExercises.java) → [StreamGatheringExercisesTest.java](../../streams/stream-gatherers/src/test/java/bg/hristomanov/education/gatherers/StreamGatheringExercisesTest.java); [JFR HTTP examples](http/jfr-lab.http) са алтернативата за production troubleshooting, не част от stream-debugger demo.

### Debugging tools — кога, кога не и компромиси

| Инструмент | Кога | Кога НЕ | Печалба / цена |
| --- | --- | --- | --- |
| Breakpoint (suspend) | Локален deterministic bug | High-traffic remote service / timeout-sensitive flow | Пълен state / влияе на scheduling и timing |
| **Logpoint** (без suspend) | Временни наблюдения без code change | Чувствителни DTO/токени, high-frequency loops, непозволен remote debugging | Много по-бърз feedback / overhead и риск от sensitive logs |
| **Stream Debugger** | Неясно преобразуване/филтриране в Stream | Infinite streams, side effects, massive datasets | Виждаш междинните стойности / може да материализира или повтори evaluation |
| **Evaluate Expression** | Проверка на чиста хипотеза | Всяка mutable/transactional операция | Моментен експеримент / потенциални side effects в JVM |
| **JFR / JMC** | Производителност и непрекъснат runtime профил | Когато трябва конкретна локална стойност на break point | Събира events без IDE suspend / sampling и интерпретация |

## 3. AI agent debugger skill

В лекцията се показва/анонсира skill за използване на IntelliJ debugger от AI агент. Наличността и интеграцията се менят между версиите; това **не означава** автоматичен или безусловен production достъп.

Безопасна лаборатория: клонирано локално repo → тестови данни без тайни → agent предлага logpoint и read-only inspection → човек разрешава → agent записва observations → човек потвърждава root cause и unit regression. Не разрешавай автоматично свързване към production JVM, изтичане на request payloads, credentials или thread dumps с лични данни към външни модели. Проверявай permission scope на plugin/agent и audit trail; изключи remote debugging, когато не е нужно.

## 4. Decision contract / финален checklist

- **Проблем:** `println` дебъгването води до шумни diff-ове и бавен feedback.
- **Кога:** локално или одобрено test/staging debugging, с минимални side effects.
- **Не:** production като универсална замяна на metrics/JFR/logs; не използвай remote debugger на публичен interface.
- **Печалби:** без source mutation, интроспекция на intermediate Stream stages, по-кратко време до хипотеза.
- **Загуби:** debugger overhead, intrusive execution, security и IDE/version dependency.
- **Алтернатива:** write targeted JUnit, instrument с безопасен logger, JFR/JMC, trace spans.
- **Доказателство:** повторяем локален test и regression; debug наблюдение само по себе си не е production evidence.

## Оригинални източници

- [Devoxx, 49:45–55:20](https://youtu.be/QBQ11aL2aZc?t=2985), Tactiq transcript.
- [IntelliJ IDEA — Analyze Java Stream Operations](https://www.jetbrains.com/help/idea/analyze-java-stream-operations.html).
- [IntelliJ IDEA — Logpoints](https://www.jetbrains.com/help/idea/logpoints.html).
- [IntelliJ IDEA — Debugging](https://www.jetbrains.com/help/idea/debugging-code.html).
- [JetBrains agent debugger skill](https://www.jetbrains.com/help/idea/ai-assisted-debugging.html) — проверявай версията/достъпността.
