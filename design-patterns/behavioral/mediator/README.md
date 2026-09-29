# Mediator — coordination без peer-to-peer coupling

## Реалният казус

Имаме:

- PaymentComponent;
- InventoryComponent;
- ShippingComponent.

Naive design:

```text
Payment → Inventory
Payment → Shipping
Inventory → Payment
Inventory → Shipping
Shipping → ...
```

С времето всеки component започва да знае всички останали.

Това прави reuse и промяната на workflow-а трудни.

## Mental model

> **Components говорят с Mediator; Mediator знае collaboration rules.**

В проекта:

- [`WorkflowMediator.java`](../src/main/java/bg/hristomanov/education/patterns/behavioral/mediator/WorkflowMediator.java);
- [`OrderWorkflowMediator.java`](../src/main/java/bg/hristomanov/education/patterns/behavioral/mediator/OrderWorkflowMediator.java);
- Payment/Inventory/Shipping components;
- [`MediatorPatternTest.java`](../src/test/java/bg/hristomanov/education/patterns/behavioral/mediator/MediatorPatternTest.java).

Success:

```text
payment completed
    ↓ mediator
inventory reserve
    ↓ mediator
shipping schedule
```

Failure:

```text
inventory failed
    ↓ mediator
payment refund
```

Components не се познават директно.

## Mediator vs Facade

Facade:

> simplified API към сложен subsystem за външен caller.

Mediator:

> communication/coordination между peer components.

Един class може визуално да прилича и на двете; intent-ът е решаващ.

## Кога да го използвам

- components са many-to-many coupled;
- промяна на един component изисква промени в много други;
- искаме components да се reuse-ват в различни collaboration contexts;
- interaction rules са по-важни от individual component logic.

## Кога НЕ

Mediator може да се превърне в God Object.

Ако цялата business logic се излее вътре, само сме преместили проблема.

## Checklist

```text
[ ] Components директно ли се познават един друг?
[ ] Coordination rules разпръснати ли са?
[ ] Mediator-ът orchestration ли държи, или вече и цялата domain logic?
[ ] Можем ли да reuse-нем component с друг mediator?
[ ] Failure/compensation semantics ясни ли са?
```

## Източници

- Refactoring.Guru — Mediator: https://refactoring.guru/design-patterns/mediator
- Design Patterns: Elements of Reusable Object-Oriented Software
