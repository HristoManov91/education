# P3 Tech Radar — JDK 28 Early Access: Simple JSON API (JEP 540)

> Devoxx 2026, Marit van Dijk / Piotr Przybył, [1:48:35–1:54:30](https://youtu.be/QBQ11aL2aZc?t=6515). **Архитектурна/версионна бележка; НЕ е runnable lab в Java25.** JDK28 все още е Early Access към ноември 2026; JEP540 предлага инкубационния модул `jdk.incubator.json`.

## 1. Какъв проблем решава

Искаме малък CLI/прототип без голям dependency graph, който прочита или създава обикновен JSON payload. JDK27 и по-рано нямат general-purpose built-in JSON tree API за това; обичайно добавяме Jackson, Gson или JSON-P. JEP540 предлага **малък JSON tree API за базови JSON стойности**, с explicit opt-in към incubator module.

```text
малък CLI / учебен пример
  → parse JSON value tree → primitive/array/object data

Spring Boot application
  → Jackson binding/configuration/security/framework integration
```

Не смесвай **JSON tree** с typed binding на Java records/POJOs, streaming parser, polymorphic custom deserializers, schema validation или Spring MVC serialization integration: JEP540 не е универсален заместител на Jackson.

## 2. Кога / кога НЕ / trade-offs

| Въпрос | Решение |
| --- | --- |
| Кога | JDK28 EA учебен CLI/prototype и малък ограничен JSON формат, след проверка на текущото incubator API. |
| Кога НЕ | Java25 production, сложен request/response mapping, streaming multi-GB JSON, Jackson annotations и Framework integration. |
| Печалба | Минимална dependency footprint за прост сценарий. |
| Цена | Incubator churn, JDK28 EA, extra `--add-modules`, ограничен feature set, възможни промени до GA. |
| По-прсто | Jackson/JSON-P за production, record DTO + Spring Boot в web приложения. |
| Доказателство | **Не е интегриран компилиран код**, защото основният reactor е Java25. За future JDK28 lab са нужни JSON roundtrip/null/duplicate-key/error тестове на точния EA build. |

## 3. Upgrade checklist (future lab)

1. Провери final JEP/API package, според точния JDK28 EA/GA build.
2. Компилирай с `--add-modules jdk.incubator.json`; добави модула и при runtime.
3. Сравни roundtrip, edge cases (`null`, escaped strings, numbers, duplicate keys, nesting), schema validation и payload limits.
4. Ако вече ползваш Jackson в Spring Boot, не сменяй библиотеката без осезаема печалба и integration tests.

**Изход:** за собствените ни Java25/Spring Boot проекти остава Jackson. Отбелязваме API-то в Tech Radar, без допълнителен Maven dependency и без преждевременен code rewrite.

## Оригинални източници

- [Devoxx, 1:48:35](https://youtu.be/QBQ11aL2aZc?t=6515).
- [JEP 540 — Simple JSON API (Incubator)](https://openjdk.org/jeps/540).
- [JDK 28 Early Access builds](https://jdk.java.net/28/).
- [RFC 8259 — JSON](https://www.rfc-editor.org/rfc/rfc8259).
