# Flyweight — споделяне на повторяемо immutable state

## Реалният казус

Имаме огромен audit stream.

Всеки event има уникални:

- requestId;
- userId;
- timestamp;
- message.

Но хиляди/милиони events повтарят една и съща metadata:

- event code;
- severity;
- category;
- schema/description.

Ако всяка instance държи пълно копие на повторяемата metadata, memory footprint расте без нужда.

## Mental model

Разделяме state на:

### Intrinsic state

Споделимо, стабилно, immutable.

При нас:

```text
LOGIN
INFO
SECURITY
```

### Extrinsic state

Уникалният context на конкретния object:

```text
requestId
userId
timestamp
message
```

В проекта:

- [`AuditEventType.java`](../src/main/java/bg/hristomanov/education/patterns/structural/flyweight/AuditEventType.java) — Flyweight;
- [`AuditEventTypeFactory.java`](../src/main/java/bg/hristomanov/education/patterns/structural/flyweight/AuditEventTypeFactory.java);
- [`AuditEvent.java`](../src/main/java/bg/hristomanov/education/patterns/structural/flyweight/AuditEvent.java) — context;
- [`FlyweightPatternTest.java`](../src/test/java/bg/hristomanov/education/patterns/structural/flyweight/FlyweightPatternTest.java).

Тестът създава 10 000 events, които сочат към една и съща shared `AuditEventType` instance.

## Защо Flyweight трябва да е immutable

Shared mutable state означава:

```text
event A ─┐
event B ─┼→ shared mutable metadata
event C ─┘
```

и една промяна влияе на всички consumers.

Затова intrinsic state трябва да бъде immutable.

## Кога да го използвам

Само когато има доказан memory pressure:

- огромен брой objects;
- значително duplicate state;
- RAM/GC pressure;
- shared state може да е immutable.

Refactoring.Guru също поставя Flyweight като pattern за ситуации с огромен брой подобни objects и реален memory проблем.

## Кога НЕ

- „за оптимизация“ при стотици objects;
- duplicate state е дребно;
- lookup/factory complexity струва повече от memory saving-а;
- shared state трябва често да се променя.

## Flyweight vs Cache

Cache пази result, за да избегне повторно computation/I/O.

Flyweight споделя **object state**, за да избегне memory duplication.

Понякога factory-то на Flyweight прилича на cache, но intent-ът е различен.

## Checklist

```text
[ ] Имаме ли измерен memory/GC проблем?
[ ] Колко instances реално създаваме?
[ ] Коя част от state-а се повтаря?
[ ] Може ли тя да бъде immutable?
[ ] Колко RAM реално спестяваме?
[ ] Допълнителната complexity оправдана ли е?
```

## Източници

- Refactoring.Guru — Flyweight: https://refactoring.guru/design-patterns/flyweight
- Design Patterns: Elements of Reusable Object-Oriented Software
