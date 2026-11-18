# Vlad Mihalcea VM-15 — Spring transaction diagnostics и code-review задачи

Вход: [VM-15 учебна глава](VM-15-SPRING-TRANSACTION-PROPAGATION.md) · [реален Java test](src/test/java/bg/hristomanov/education/uow/VladSpringTransactionsTest.java).

## 6 самостоятелни mini-case-а

1. **Catch + REQUIRED.** Outer записва \`A\`, inner участва с \`REQUIRED\`, записва \`B\` и хвърля unchecked exception. Outer го хваща. Предскажи дали A/B са persisted и защо получаваме \`UnexpectedRollbackException\`.
2. **REQUIRES_NEW.** Outer записва A, inner от друг bean записва B с \`REQUIRES_NEW\`, outer се rollback-ва. Кои данни са видими след края? Какви ресурси ще се изискват при 100 concurrent HTTP requests?
3. **NESTED.** Искаш partial rollback на един JDBC insert, без да губиш outer transaction. Защо подходящ е savepoint, а не отделна physical transaction? Защо това не е автоматично JPA nested context?
4. **Self invocation.** \`public foo() { this.bar(); }\` и \`@Transactional public bar()\`: ако foo е извикан през bean proxy, bar получава ли own transaction? Покажи evidence.
5. **Checked exceptions.** Ако метод с \`@Transactional\` insert-не ред и хвърли \`IOException\`, какъв е default commit behavior? Как го променяш чрез \`rollbackFor\` или обща политика?
6. **OSIV.** Може ли \`OpenEntityManagerInView\` да скрие lazy loading bug? Доказва ли OSIV физически connection lease през целия HTTP request? Какви метрики трябва да събереш?

## Практически команди

~~~bash
mvn -pl persistence/repository-unit-of-work test
~~~

## Senior decision exercise

Получаваш endpoint, който:
- пише business record;
- извиква външен HTTP API;
- записва audit събитие;
- после праща Kafka message.

Сравни три подхода: 1) една широка \`@Transactional\` граница; 2) \`REQUIRES_NEW\` за audit; 3) DB transaction + [Transactional Outbox](../../spring/messaging/outbox-inbox/README.md). За всеки опиши correctness, partial failures, retries, idempotency, DB connection occupancy и debugging complexity.

**Изход:** успешни тестове доказват кодова семантика, но не означават автоматически, че темата е усвоена; отбелязваме успех след реални отговори.
