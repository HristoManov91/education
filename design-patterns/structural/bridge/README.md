# Bridge — две независими dimensions без class explosion

## Реалният казус

Имаме два вида alerts:

- operational;
- security.

И два transports:

- email;
- Slack.

Ако моделираме всичко само чрез inheritance:

```text
EmailOperationalAlert
SlackOperationalAlert
EmailSecurityAlert
SlackSecurityAlert
```

При още 4 alert types и още 3 transports комбинациите растат бързо.

## Root cause

Имаме **две независими причини за промяна**, натъпкани в една hierarchy:

1. high-level alert behavior;
2. delivery implementation.

## Mental model

> **Bridge разделя двете dimensions и ги свързва чрез composition.**

В проекта:

- [`Alert.java`](../src/main/java/bg/hristomanov/education/patterns/structural/bridge/Alert.java) — abstraction;
- [`OperationalAlert.java`](../src/main/java/bg/hristomanov/education/patterns/structural/bridge/OperationalAlert.java);
- [`SecurityAlert.java`](../src/main/java/bg/hristomanov/education/patterns/structural/bridge/SecurityAlert.java);
- [`AlertTransport.java`](../src/main/java/bg/hristomanov/education/patterns/structural/bridge/AlertTransport.java) — implementation contract;
- Email/Slack implementations;
- [`BridgePatternTest.java`](../src/test/java/bg/hristomanov/education/patterns/structural/bridge/BridgePatternTest.java).

```text
Alert hierarchy
      │
      └──── bridge ───→ AlertTransport hierarchy

Operational             Email
Security                Slack
```

Добавяме нов alert type без да пипаме transport hierarchy и обратно.

## Bridge vs Strategy

И двете използват composition + interface.

Strategy обикновено отговаря:

> „Кой algorithm да използвам?“

Bridge отговаря:

> „Имам две независими hierarchies, които трябва да се развиват независимо.“

## Bridge vs Adapter

Adapter се появява често **след** като имаме несъвместими interfaces и искаме да ги свържем.

Bridge обикновено е **предварителен design decision**, който разделя две axes of variation.

## Кога да го използвам

- class hierarchy експлодира от комбинации;
- имаме platform/provider × business-variant matrix;
- abstraction и implementation имат независим lifecycle;
- искаме runtime да сменяме implementation.

## Кога НЕ

Ако имаме само една реална dimension и 2–3 стабилни classes, Bridge може да е ненужна abstraction.

## Checklist

```text
[ ] Имаме ли две независими axes of change?
[ ] Създаваме ли subclasses за всяка комбинация?
[ ] Може ли едната axis да стане injected implementation object?
[ ] И двете hierarchies имат ли реална самостоятелна еволюция?
```

## Източници

- Refactoring.Guru — Bridge: https://refactoring.guru/design-patterns/bridge
- Design Patterns: Elements of Reusable Object-Oriented Software
