# Learning Progress — актуален отчет

**Snapshot дата:** 2026-10-08 (Europe/Sofia)  
**Цел:** системна диагностика от Junior fundamentals към уверен Mid+ и Early Senior.  
**Източник на оценките:** действителни отговори в диалог + обоснована обратна връзка. Не попълвай проценти без проведена проверка.  
**Протокол:** [ASSESSMENT-PROTOCOL.md](ASSESSMENT-PROTOCOL.md) · **План:** [ROADMAP.md](ROADMAP.md)

## Overview по области

| Област | Приоритет | Диагностика | Доказана степен |
| --- | --- | --- | --- |
| Java Core, Collections, Concurrency, JVM | **P0** | **Започната** — Java fundamentals | Все още **неопределена** |
| SQL, Database Performance, Persistence | **P0** | Не е започната | Непроверено |
| Spring Core / Web / Security | **P0** | Не е започната | Непроверено |
| Testing & Reliability | **P0** | Не е започната | Непроверено |
| System Design, Distributed Systems | P1 | Не е започната | Непроверено |
| JVM Diagnostics / Observability | P1 | Не е започната | Непроверено |
| DS&A | P1 | Не е започната тази диагностика | Непроверено |
| Caching, Containers, AI Engineering | P1 | Не е започната тази диагностика | Непроверено |
| Design Patterns / Advanced Architecture | P2 | Не е започната тази диагностика | Непроверено |

> Има изградени лаборатории/обучителни материали и по други теми, но **наличието им не е доказателство**, че знанията вече са проверени или усвоени.

## Текущо: Java Fundamentals — начална диагностика

| Поле | Състояние |
| --- | --- |
| Активна родителска тема | Java Fundamentals 1.1 — primitives, references, mutation, method calls и String immutability |
| Последна активност | **2026-10-08** |
| Статус | **DIAGNOSTIC_IN_PROGRESS** — напредваме въпрос по въпрос |
| Вече отговорени | **Въпрос 1:** primitive/reference копиране; **Въпрос 2:** method arguments/pass-by-value; **Въпрос 3:** String и StringBuilder |
| Въпрос 1 — ориентировъчно | **90% само за Q1** (от предишната оценка); правилни изходи: `10`, `20`, `AB`, `AB` |
| Въпрос 2 — оценка от обратната връзка | **9.5/10 само за Q2**; правилни `Maria` и `20`, правилно обяснение на mutation и reassignment |
| Въпрос 3 — оценка от обратната връзка | **8/10 разбиране; 8.5/10 комуникация — само Q3**; правилни `Hello` и `Hello World` и правилно обяснение за `concat()`; допуснато неточно обяснение на `StringBuilder.append()` |
| Терминология за затвърждаване | **value vs reference vs object**, mutation vs reassignment; `append()` изменя същия builder, не създава нов `StringBuilder`; не всяка стара `String` стойност задължително се събира от GC |
| Следващо действие | **Кратко проверяващо упражнение Q3b** за identity и reassignment на `StringBuilder`; след това `JAVA-FND-03` (`null`, `==`, `equals()`), ако няма изискуем recall |
| Финална оценка на Java Fundamentals | **Няма достатъчно данни** — оценките са само за отделни въпроси |

### Какво вече доказаха отговорите

- **Q1:** независимо копиране на примитивна стойност; две референции към един споделен изменяем обект. Корекция: и примитивни стойности, и референции, и обекти заемат памет; не твърдим фиксирано stack/heap правило.
- **Q2:** `p` и `person` са независими променливи; копира се стойността на референцията; `person.name = "Maria"` изменя споделения обект; `person = new Person("Peter")` пренасочва само локалния параметър; `age = 30` променя локалното копие.
- **Q3:** `String.concat(" World")` не изменя `String` обекта; локалното `text` не променя `main`; `builder` от `main` става `Hello World`. **Остава да се провери независимо** че `append()` изменя същия `StringBuilder` екземпляр и връща него, вместо да създава нов `StringBuilder`.

**Постоянни материали за преговор и NotebookLM:**
[Q1 — Values, References & Aliasing](concepts/01-values-references-aliasing.md) ·
[Q2 — Method Arguments: Pass-by-Value](concepts/02-method-arguments-pass-by-value.md) ·
[Q3 — String & StringBuilder](concepts/03-string-immutability-stringbuilder.md).

### Recall queue — поправено знание и професионален изказ

| Кога | Дата (Europe/Sofia) | Състояние | Проверка |
| --- | --- | --- | --- |
| D+1 | **2026-10-09** | PENDING | Обясни `value/reference/object` и `mutation/reassignment`; в нов snippet — `concat()` срещу `append()`, без да четеш бележката |
| D+7 | **2026-10-15** | PENDING | Променен сценарий с параметър, alias, `String` и `StringBuilder`; обясни страничния ефект и точната терминология |
| D+30 | **2026-11-07** | PENDING | Кратка интеграционна задача с refs, method args, String/Builder и професионално обяснение |

**Когато се върнем:** проверявай изискуемите преговори **преди** новите въпроси. „PENDING“ не е неуспех, а непроведена проверка. Датите не са автоматични reminders.

### Следващият все още неотговорен въпрос: Q3b

Цел: независима проверка дали `StringBuilder.append()` връща същия екземпляр и дали пренасочването на едната променлива променя другата.

```java
StringBuilder first = new StringBuilder("A");
StringBuilder second = first.append("B");

first = new StringBuilder("C");
second.append("D");

System.out.println(first == second);
System.out.println(first);
System.out.println(second);
```

**Питай:** „Какво ще отпечата кодът и защо? По-конкретно каква е идентичността на `first` и `second` преди и след reassignment?“ **Не показвай решението предварително.**

## Детайлен progress по микро-теми

| ID | Status | Доказателства до момента | Изказ/корекция | Последна активност | Следващо действие |
| --- | --- | --- | --- | --- | --- |
| JAVA-FND-01 | `TARGETED_REVIEW` | Q1: резултатите са верни; ориентировъчно 90% за Q1 | Примитивна стойност vs референция vs обект, mutable state | 2026-10-08 | D+1, D+7, D+30 recall |
| JAVA-FND-02 | `READY_FOR_RECALL` | Q2: 9.5/10, правилно обяснява pass-by-value и локален reassignment | Копие на *стойността на референцията* | 2026-10-08 | D+1, D+7, D+30 recall |
| JAVA-FND-03 | `NOT_TESTED` | — | — | — | Нов въпрос след Q3b/изискуемите recall |
| JAVA-FND-04 | `TARGETED_REVIEW` | Q3: 8/10 технически, 8.5/10 изказ — само за въпроса | `append()` изменя **същия обект**, не конструира друг builder | 2026-10-08 | Q3b, после D+1, D+7, D+30 |
| Други микро-теми | `NOT_TESTED` | — | — | — | Избери по ROADMAP след prerequisites |

## Дневник на заниманията

### 2026-10-08 · Java Fundamentals · Session 1 (текуща, частична)

| Въпрос | Проверка | Реално показан резултат | Корекция |
| --- | --- | --- | --- |
| Q1 | `int` copy; `StringBuilder` aliasing | `10`, `20`, `AB`, `AB`, правилни; асистентът даде 90% ориентировъчно само за Q1 | Примитиви, референции и обекти всички имат представяне в паметта |
| Q2 | `Person` argument, field mutation, reassignment, primitive parameter | `Maria`, `20`, правилни; асистентът даде 9.5/10 за Q2 | „Копира се стойността на референцията“, не „подава се самата променлива“ |
| Q3 | `String.concat` vs `StringBuilder.append` | `Hello`, `Hello World`, правилни; 8/10 техн. + 8.5/10 комуникация за Q3 | `append()` изменя съществуващия builder и връща него; GC не е автоматична гаранция |

- **Силна страна:** добър mental model за независима примитивна променлива, копирана референция и локално пренасочване.
- **Конкретен оставащ пропуск:** разбиране на идентичността на `StringBuilder` след `append()` и разграничаване от нов обект.
- **Материали:** добавена [Concept Notes библиотека](concepts/README.md) с три самостоятелни Markdown обяснения и аудио текст.
- **Не е правено:** Q3b и spaced recall; **не** отбелязвай успех на тези проверки.
- **Следващ реален въпрос:** Q3b от секцията по-горе.

---

**Правило за поддръжка:** след всяка действително проведена сесия актуализирай overview, детайлната таблица, review queue и дневника. Оценките са само за реални отговори. Concept Notes се поддържат отделно като трайна техническа документация.
