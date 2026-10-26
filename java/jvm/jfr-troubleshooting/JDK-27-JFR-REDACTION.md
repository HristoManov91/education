# P2 Version Note — JFR In-Process Data Redaction (JDK 27)

> **Source:** [Modern Java in Practice — JFR ~2:28–2:34](https://youtu.be/zC2tbq7S5UE?t=8880) и официалният [JEP 536](https://openjdk.org/jeps/536).  
> **Version note:** Java 27 feature; **не е Java 25 runnable test**. Основният [JFR course](README.md) остава на Java 25.

## Production failure: incident recording leaks sensitive information

В production събираме `.jfr` dump, за да диагностицираме GC pause. Той може да включва:
- JVM/application command-line arguments;
- initial system properties;
- initial environment variables;
- някои runtime flags и други diagnostic events.

Ако start command съдържа `-Dservice.token=...` или environment има `DB_PASSWORD`, изпращането на raw recording към support ticket може да изложи credentials.

**Не записвай действителни passwords/API keys в тестовите команди и логове.**

## 1. Какво се промени в JDK 27?

JEP 536 добавя **in-process, name/pattern-based redaction** на чувствителни JVM аргументи и началните стойности на environment variables и system properties **преди** да бъдат изнесени към JFR recording. JDK 27 включва набор default filters за типични sensitive names, но не трябва да разчитаме на него за arbitrary secret formats.

### Измерим configuration пример за JDK 27

```bash
java "-XX:FlightRecorderOptions:redact-key=+*internal*credential*" \
  "-XX:StartFlightRecording:filename=target/jfr-redacted.jfr,dumponexit=true" \
  -Dinternal.credential=DUMMY_DO_NOT_USE \
  -cp target/classes com.example.Demo
```

Това е **version-specific illustration** — не е пълна runnable command за education module (няма com.example.Demo в този project). Реални JFR командни примери са в [JDK-25-JFR.md](JDK-25-JFR.md).

За аргументи е наличен и `redact-argument`. Префиксът `+` **запазва default filters**, докато разширява конфигурацията; не заменяй defaults без security review.

Потвърди поддръжката и настройките на конкретния JDK 27 build с:

```bash
java -XX:FlightRecorderOptions:help
```

След record провери с `jfr print` дали очакваното dummy secret value е маскирано. Не показвай в публичен pipeline реални secret values като "доказателство".

## 2. Какво JFR redaction НЕ прави

- Не маскира автоматично всеки custom JFR event, който **приложението** записва с password/token полета.
- Не защитава логове, exception stacktrace, HTTP bodies, heap dumps или memory profiler-ите извън JFR.
- Не замества секретно хранилище, least-privilege access, encryption at rest и retention policy.
- Не обещава, че всеки credential във free-form string е открит.
- Не прави безопасно да споделяш безконтролно production recording.

**Не изключвай default redaction само за по-лесен debugging**. Ако diagnostics става по-трудна, използвай dummy/test credentials или по-тесни анонимизирани data.

## 3. Оставащият JFR learning flow

Вече имаме [Java 25 — JFR Method Timing & Tracing](JDK-25-JFR.md), CPU sampling, memory pressure, contention, file I/O, custom business events и JMC.

Затова тук добавяме само:
- новия privacy/security risk;
- настройки JDK 27;
- release/version compatibility;
- review checklist за sensitive recordings.

### Code review checklist

- [ ] JFR attachments към issue/incident съдържат ли тайни?
- [ ] Custom events избягват ли PII/tokens?
- [ ] Има ли ограничен достъп и retention policy?
- [ ] Проверени ли са JDK version/default filter patterns?
- [ ] Redaction проверена ли е **преди** recording да напусне производствената среда?

## Оригинални източници

- [Devoxx 2026](https://youtu.be/zC2tbq7S5UE)
- [JEP 536 — JFR In-Process Data Redaction](https://openjdk.org/jeps/536)
- [Oracle Java 27 release notes — redact-key and redact-argument](https://www.oracle.com/java/technologies/javase/27all-relnotes.html)
- [Inside Java — Java 27 Runtime Updates](https://inside.java/2026/09/12/jdk-27-runtime-updates/)
