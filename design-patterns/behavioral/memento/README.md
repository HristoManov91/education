# Memento — snapshot и restore без счупване на encapsulation

## Реалният казус

Имаме editor за pricing rules.

Потребителят:

1. прави валидна конфигурация;
2. започва промени;
3. прави грешни промени;
4. иска Undo.

Naive решение:

```text
History service чете всички private fields
→ копира ги
→ после ги set-ва обратно
```

Така external object трябва да знае вътрешната структура на editor-а.

## Mental model

Memento има три роли:

### Originator

Object-ът, чието state пазим.

При нас: `PricingRuleEditor`.

### Memento

Opaque immutable snapshot.

### Caretaker

Знае **кога** да пази/restore-ва, но не знае какво има вътре.

При нас: `PricingRuleHistory`.

Код:

- [`PricingRuleEditor.java`](../src/main/java/bg/hristomanov/education/patterns/behavioral/memento/PricingRuleEditor.java)
- [`PricingRuleHistory.java`](../src/main/java/bg/hristomanov/education/patterns/behavioral/memento/PricingRuleHistory.java)
- [`MementoPatternTest.java`](../src/test/java/bg/hristomanov/education/patterns/behavioral/memento/MementoPatternTest.java)

## Memento vs Prototype

Prototype:

> „Искам нов independent object, базиран на този.“

Memento:

> „Искам да запазя минал state, за да възстановя същия object.“

Понякога implementation-ът може да използва копиране, но intent-ът е различен.

## Кога да го използвам

- undo/redo;
- workflow/config editor;
- temporary rollback на in-memory state;
- checkpointing;
- history на сложен encapsulated object.

## Кога НЕ

Не използвай in-memory Memento вместо database transaction.

При persistence rollback имаме отделни transactional semantics, concurrency и durability.

## Цена

Snapshots могат да бъдат скъпи по memory, особено ако state-ът е голям.

В такива случаи може да са по-подходящи:

- diffs;
- event log;
- command history;
- persistence snapshots.

## Checklist

```text
[ ] Кой е originator-ът?
[ ] Snapshot-ът immutable ли е?
[ ] Caretaker-ът остава ли blind към private state?
[ ] Full snapshot или diff е по-подходящ?
[ ] Колко memory ще струва history-то?
[ ] Нужно ли е state-ът да оцелее process restart?
```

## Източници

- Refactoring.Guru — Memento: https://refactoring.guru/design-patterns/memento
- Design Patterns: Elements of Reusable Object-Oriented Software
