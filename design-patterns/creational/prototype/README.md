# Prototype — копиране на вече конфигуриран object

## Реалният казус

Имаме report templates с много предварителна конфигурация:

- колони;
- default filters;
- output настройки;
- permissions;
- formatting правила.

Naive вариантът е при всяко създаване да повтаряме construction logic-а или caller-ът да знае concrete class-а и да копира всички полета.

Това има два проблема:

1. client-ът се coupling-ва към concrete implementation;
2. лесно пропускаме private/nested/mutable state при копиране.

## Mental model

> **Prototype казва на object-а сам да знае как да направи свое независимо копие.**

В проекта:

- [`ReportTemplate.java`](../src/main/java/bg/hristomanov/education/patterns/creational/prototype/ReportTemplate.java)
- [`CsvReportTemplate.java`](../src/main/java/bg/hristomanov/education/patterns/creational/prototype/CsvReportTemplate.java)
- [`ReportTemplateRegistry.java`](../src/main/java/bg/hristomanov/education/patterns/creational/prototype/ReportTemplateRegistry.java)
- [`PrototypePatternTest.java`](../src/test/java/bg/hristomanov/education/patterns/creational/prototype/PrototypePatternTest.java)

```text
preconfigured template
       ↓ copy()
new independent template
       ↓
caller customizes copy
```

## Защо не Java Cloneable?

Този урок умишлено използва explicit copy contract + copy constructor.

`Cloneable` има исторически неудобен API и лесно води до неясно shallow-copy поведение. За учебен и production-like код copy constructor/factory обикновено прави semantics по-видими.

## Shallow vs deep copy

Ако original и clone сочат към една и съща mutable collection:

```text
original.filters ─┐
                  ├→ same Map
clone.filters    ─┘
```

промяна в clone-а променя original-а.

Нашият copy constructor създава нови collections.

## Кога да го използвам

- construction/configuration е скъп или подробен;
- имаме готови presets/templates;
- client-ът не трябва да знае concrete class-а;
- копираме polymorphic objects;
- искаме алтернатива на много subclasses, които се различават само по initial configuration.

## Кога НЕ

- object-ът е малък immutable record;
- constructor/static factory е по-ясен;
- object-ът държи sockets, threads, transactions или други resources, които нямат смислено copy semantics;
- graph-ът има сложни circular references и deep copy става рисков.

## Как да го разпозная

```text
"Вземи тази конфигурация като основа и направи нова независима версия"
```

е силен сигнал за Prototype.

## Checklist

```text
[ ] Копието независимо ли е от original-а?
[ ] Кои полета са immutable и могат безопасно да се share-ват?
[ ] Кои mutable collections/objects трябва да се deep-copy-нат?
[ ] Има ли external resources, които НЕ трябва да се копират?
[ ] Client-ът печели ли нещо от това да не знае concrete class-а?
```

## Източници

- Refactoring.Guru — Prototype: https://refactoring.guru/design-patterns/prototype
- Design Patterns: Elements of Reusable Object-Oriented Software
