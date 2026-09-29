# Visitor — нови операции върху стабилна object hierarchy

## Реалният казус

Имаме стабилни payment types:

- CardPayment;
- BankTransferPayment;
- VoucherPayment.

После започват да се добавят операции:

- processing fee;
- compliance label;
- export;
- reporting;
- audit formatting.

Ако всяка auxiliary operation влезе в payment classes:

```text
CardPayment
  ├→ fee()
  ├→ complianceLabel()
  ├→ exportCsv()
  ├→ exportXml()
  └→ ...
```

domain types започват да трупат несвързани отговорности.

## Mental model

> **Visitor мести operation family извън element hierarchy и използва double dispatch, за да избере правилната операция за concrete element type.**

В проекта:

Elements:

- [`PaymentElement.java`](../src/main/java/bg/hristomanov/education/patterns/behavioral/visitor/PaymentElement.java)
- `CardPayment`
- `BankTransferPayment`
- `VoucherPayment`

Visitors:

- [`ProcessingFeeVisitor.java`](../src/main/java/bg/hristomanov/education/patterns/behavioral/visitor/ProcessingFeeVisitor.java)
- [`ComplianceLabelVisitor.java`](../src/main/java/bg/hristomanov/education/patterns/behavioral/visitor/ComplianceLabelVisitor.java)

Test:

- [`VisitorPatternTest.java`](../src/test/java/bg/hristomanov/education/patterns/behavioral/visitor/VisitorPatternTest.java)

## Силата на Visitor

Ако **element types са стабилни**, а новите operations са чести:

```text
нов Visitor
→ без промяна в CardPayment/BankTransfer/Voucher
```

Това е добър fit.

## Големият trade-off

Ако добавим нов element:

```text
CryptoPayment
```

трябва да добавим `visitCrypto(...)` във **всеки visitor**.

Затова Visitor обръща Open/Closed trade-off-а:

- лесно добавяме операции;
- трудно добавяме нови element types.

## Visitor vs Strategy

Strategy избира един algorithm за context.

Visitor обикновено изпълнява operation върху **heterogeneous object structure**, където behavior-ът зависи от concrete element type.

## Кога да го използвам

- object hierarchy е стабилна;
- operations растат;
- auxiliary behavior не принадлежи естествено в domain objects;
- имаме tree/graph/AST-like structure.

## Кога НЕ

Ако добавяме нови element types всяка седмица, Visitor ще създаде shotgun changes по всички visitors.

В модерна Java sealed hierarchy + pattern matching понякога е по-просто. Pattern-ът трябва да се сравни с езиковите възможности, не да се прилага механично.

## Checklist

```text
[ ] Element hierarchy стабилна ли е?
[ ] Новите operations по-чести ли са от новите element types?
[ ] Auxiliary logic замърсява ли core domain classes?
[ ] Double-dispatch complexity оправдана ли е?
[ ] Pattern matching би ли бил по-прост?
```

## Източници

- Refactoring.Guru — Visitor: https://refactoring.guru/design-patterns/visitor
- Design Patterns: Elements of Reusable Object-Oriented Software
