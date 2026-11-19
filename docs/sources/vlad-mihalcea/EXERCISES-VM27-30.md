# VM-27–VM-30 — упражнения за самопроверка

## VM-27 Envers / CDC

1. В [AuditingAndInheritanceTest](../../../persistence/hibernate-performance/src/test/java/bg/hristomanov/education/hperf/AuditingAndInheritanceTest.java) има две commit-нати промени. Колко revisions очакваме и защо?
2. Ако отделна система изпълни direct JDBC UPDATE, кой ще разбере — Envers, Debezium, Outbox, или всички?
3. Защо запис на audit в remote HTTP service по време на DB transaction може да е некоректен? Кога е нужен outbox?

## VM-28 inheritance

4. Какво губим и печелим при JOINED спрямо SINGLE_TABLE, TABLE_PER_CLASS и @MappedSuperclass?
5. Виж [TransportAsset](../../../persistence/hibernate-performance/src/main/java/bg/hristomanov/education/hperf/domain/TransportAsset.java) и теста. Коя таблица съдържа общата identity и кои са subtype таблиците?

## VM-29 SQL

6. Защо recursive CTE без cycle detection може да не завърши? Защо maxDepth сам по себе си не доказва business correctness?
7. При двама служители с равен score как се различават RANK и ROW_NUMBER? Виж [PostgresHierarchyAnalyticsIT](../../../database/sql-labs/src/test/java/bg/hristomanov/education/sql/PostgresHierarchyAnalyticsIT.java).

## VM-30 upgrade

8. Ако Hibernate 7 премине в 8, кой test ще сигнализира? Защо H2 не доказва PostgreSQL/Oracle portability? Кои JPA/DB проверки пускаме преди deployment?

## Run

```bash
mvn -pl persistence/hibernate-performance test
mvn -pl persistence/hibernate-performance -Ppostgres-integration verify
mvn -pl database/sql-labs -Ppostgres-integration verify
```

**Изходно оценяване:** кодът и CI са доказателства за поведението на лабораториите, а не за лично усвоени знания. Приложи [Assessment Protocol](../../learning/ASSESSMENT-PROTOCOL.md) върху собствените отговори.
