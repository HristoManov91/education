# NotebookLM — VM-15 Spring transaction propagation, AOP proxies и rollback

## Източници

- [VM-15 подробно обучение](VM-15-SPRING-TRANSACTION-PROPAGATION.md).
- [VladSpringTransactionsTest](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java).
- [Inner service](src/main/java/bg/hristomanov/education/uow/service/VladTransactionInnerService.java), [Outer orchestration](src/main/java/bg/hristomanov/education/uow/service/VladTransactionOrchestrationService.java), [Self-invocation](src/main/java/bg/hristomanov/education/uow/service/VladSelfInvocationProbe.java).
- [Задачи и диагностика](VM-15-EXERCISES.md).
- [Vlad — Spring Transaction and Connection Management](https://vladmihalcea.com/spring-transaction-connection-management/).
- [Spring — Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html) и [Annotations](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).

## Audio Overview prompt

> Подготви задълбочен аудио урок на **български** за Mid+ → Early Senior Java/Spring backend developer, който вече знае JDBC/Hibernate fundamentals. Не започвай с термините от API. Започни с production incident: inner метод хвърля unchecked exception, outer catch не помага, виждаме UnexpectedRollbackException; след това audit REQUIRES_NEW оцелява след outer rollback и виждаме partial success.
>
> Обясни **логическа transaction scope vs physical JDBC transaction vs physical connection lease** и защо Spring proxy interception е съществено. Използвай РЕАЛНИТЕ резултати от шестте ни теста: REQUIRED rollback-only, REQUIRES_NEW commit, NESTED JDBC savepoint, self-invocation false vs proxy true, checked exception default commit vs rollbackFor rollback.
>
> Сравни REQUIRED, REQUIRES_NEW, NESTED, SUPPORTS, MANDATORY, NOT_SUPPORTED, NEVER, но не твърди, че тестовете доказват всички режими. За всяко решение обясни **какъв проблем решава, кога е подходящо/кога НЕ, печалби/загуби, по-проста алтернатива и как да го докажем**. Покажи pool exhaustion warning при REQUIRES_NEW и защо NESTED не е REQUIRES_NEW. Разграничи Java exception catch от Spring rollback-only status, OSIV от physical JDBC lease и readOnly hint от database-enforced read-only. Спомени Spring global ALL_EXCEPTIONS policy като алтернатива на default rollback rules.
>
> Финал: 5 практически въпроса за интервю/code review; даден проблем с външен HTTP call и Kafka събитие да се сравни с Transactional Outbox. Не измисляй benchmark numbers, които тестовете не измерват.

Аудиото не заменя самостоятелното изпълнение на [тестовете и упражненията](VM-15-EXERCISES.md).
