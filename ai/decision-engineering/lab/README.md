# Java 25 / JUnit — Legacy Claims Modernization Lab

## Case

Синтетичен застрахователен claims service: при липсващи документи връщаме NEED_DOCUMENTS; иначе при suspected fraud — MANUAL_REVIEW; иначе суми строго над 100 000 цента изискват review, с историческо partner изключение. В учебния казус SME изрично одобрява премахването на bypass; това НЕ е реален застрахователен договор.

## README → код

- [Claim](src/main/java/bg/hristomanov/education/ai/claims/Claim.java) и [Outcome](src/main/java/bg/hristomanov/education/ai/claims/Outcome.java): input и observable contract.
- [LegacyClaimsService](src/main/java/bg/hristomanov/education/ai/claims/LegacyClaimsService.java): old nested conditions, observed behavior.
- [RefactoredClaimsService](src/main/java/bg/hristomanov/education/ai/claims/RefactoredClaimsService.java): guard clauses + extracted predicate, същото поведение.
- [ApprovedClaimsPolicy](src/main/java/bg/hristomanov/education/ai/claims/ApprovedClaimsPolicy.java): отделна policy change, не refactor.
- [ClaimsModernizationTest](src/test/java/bg/hristomanov/education/ai/claims/ClaimsModernizationTest.java): 6 JUnit метода; 48 boundary combinations в parity matrix и 48 в approved-change matrix, frozen fixtures, explicit counterexample, invalid inputs.

| Input (docs present, no fraud) | Legacy/refactor | Approved policy |
| --- | --- | --- |
| 100 000 цента, non-partner | APPROVED | APPROVED |
| 100 001 цента, non-partner | MANUAL_REVIEW | MANUAL_REVIEW |
| 100 001 цента, partner | APPROVED | MANUAL_REVIEW |

От root на repository с Java 25 и Maven:

    mvn -B -f ai/decision-engineering/lab/pom.xml test

Lab е включен и в root Maven reactor. Не изисква LLM ключове, DB, network services или Docker при изпълнение на тестовете (Maven трябва да има dependencies). Не е production rollout, не е реален LLM evaluation и не симулира плащане. CI трябва да потвърди JDK25/JUnit. Expected values са независими фиксирани fixtures, но са авторски учебни правила, не външно бизнес одобрение.

## Exercise

Първо обясни защо parity test минава за legacy/refactor. После пробвай временно да махнеш partner bypass в refactor: frozen fixture и matrix трябва да хванат промяната. Върни refactor; policy variant остава различен само в одобрения subset. Не редактирай expected values, за да стане green.

## Decision contract

Използвай characterization за неизвестна legacy логика преди refactor, но не като единствен oracle за correctness. Не е достатъчно за I/O, concurrency, security и external side effects; там добави integration/contract tests. Печелим проверим behavior preservation; губим maintenance на fixtures и можем да закрепим legacy bug. Алтернатива: директна policy промяна с независима acceptance spec, ако старото поведение не трябва да се запази. Запазваме разделение old observed vs approved expected; паритетът не е бизнес одобрение.

## Оригинални източници

[Lecture/evidence map](../SOURCES-AND-EVIDENCE.md) · [Refactoring](../03-AI-REFACTORING.md) · [Archaeology](../04-AI-ARCHAEOLOGY.md).
