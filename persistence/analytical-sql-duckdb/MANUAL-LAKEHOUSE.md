# P2 · DuckLake / Federation / Quack — архитектурно продължение (без облачни тестове)

> Основано на [Oliver Geisser — From Laptop to Lakehouse](https://www.youtube.com/watch?v=B5-4mDLrWjM), предоставен SRT, ~10:55–27:45. По време на лекцията Quack е alpha и скоро очакван; **DuckDB 2.0.0 LTS от 21 октомври 2026 вече включва Quack**. Ползвай официалната версия на командите, не стар alpha syntax. Това е **manual decision guide**, а не running multi-user/S3 integration test.

## 1. DuckDB, DuckLake и Quack са различни слоеве

| Обект | Решава | Кога | Кога не / компромис |
| --- | --- | --- | --- |
| **DuckDB** | вграден analytical SQL engine; CSV/Parquet, JDBC | local ad-hoc analysis | shared OLTP multiwriter replacement |
| **DuckLake** | metadata в SQL catalog + open Parquet data files, snapshots/schema changes | споделен lakehouse, object storage | малък локален анализ върху един CSV |
| **Quack** | remote protocol между DuckDB clients/server | remote/multi-client setup | когато работим само в един Java процес |

В lecture example DuckLake е **SQL metadata catalog**, не нов самостоятелен database engine. Съхранява data files в Parquet, локално или S3. Native DuckDB файл с read-write lock за един процес не означава, че обикновеното споделяне на файла осигурява distributed transactions.

## 2. Избор на каталог за DuckLake 1.1

| Ситуация | Документиран избор | Ограничения |
| --- | --- | --- |
| Един локален analyst | DuckDB catalog | един процес записва, освен ако всички четат read-only |
| Няколко локални processes | SQLite catalog | едновременните writers се сериализират/retry |
| Многопотребителски отдалечен lakehouse | PostgreSQL catalog | отделен service, permissions/HA/isolation |
| DuckDB catalogs през remote clients | Quack | network/auth/port/service ownership |

Официално: [DuckLake catalog choices](https://ducklake.select/docs/stable/duckdb/usage/choosing_a_catalog_database). **DuckLake 1.1 изисква DuckDB >=2.0.0.**

## 3. Ръчни SQL примери — само в отделна локална sandbox директория

### A. Локален DuckLake с локални data files (инсталира extension при първо изпълнение)

~~~sql
INSTALL ducklake;
LOAD ducklake;
ATTACH 'ducklake:metadata.ducklake'
  AS demo_lake (DATA_PATH 'data_files/');
USE demo_lake;
CREATE TABLE observations (plate VARCHAR, at TIMESTAMP);
INSERT INTO observations VALUES ('DEMO-1', TIMESTAMP '2026-11-01 10:00:00');
SELECT * FROM observations;
~~~

Кодът е **пример за ръчно изпълнение**, не тестван от нашия Java25 JUnit. `INSTALL` може да ползва мрежа и trusted extension repository.

### B. PostgreSQL catalog + S3 (схематичен пример; не копирай дословно към production)

~~~sql
INSTALL ducklake;
INSTALL postgres;
ATTACH 'ducklake:postgres:dbname=approved_catalog host=approved_host'
  AS team_lake (DATA_PATH 's3://approved-bucket/data/');
USE team_lake;
~~~

Тук предварително се изискват PostgreSQL/catalog права, одобрен bucket, `httpfs`, валидни IAM credentials и разрешения. Няма истински адрес или тайна в примерите и никакви credentials не се записват в GitHub.

### C. Quack remote SQL

DuckDB 2.0 [Quack protocol](https://duckdb.org/quack/) може да обслужва multiple remote clients; за тази задача **не отваряме слушащ порт** в GitHub Actions и не симулираме успешна Quack integration. `CALL quack_serve(...)`, `ATTACH 'quack:...'` и `CONNECT` са version-dependent; ползвай актуалния official quickstart и real authentication/authorization/threat model, не alpha commands от записа.

## 4. Федеративни заявки

Oliver показва как DuckDB може да чете външни CSV/Parquet/S3 файлове и да `ATTACH` SQLite/Postgres, после да изпълнява cross-source joins. Това е удобно за **read-only snapshots/approved datasets**, но:
- няма единна distributed транзакция по подразбиране;
- remote JOIN може да прочете големи обеми през мрежата и да натовари OLTP source;
- „един query“ ≠ един консистентен timestamp във всички системи;
- първо изясни identity/permissions, type conversions, credentials и data residency.

**По-проста алтернатива:** експортирай консистентен snapshot във versioned Parquet, изпълни SQL локално. Ако отчетът е евтин за Oracle/Postgres, няма причина да изграждаш lakehouse.

## 5. Security и operations

Persistent secrets в DuckDB се пазят извън source code, но **не са encrypted on disk** според [официалния security guide](https://duckdb.org/docs/2.0/operations_manual/securing_duckdb/overview). За production използвай краткоживеещи токени/IAM, ограничен `SCOPE` и secret rotation, не пароли в SQL или логове. Разширенията имат права на хост процеса; ограничавай доверени разширения и външни файлови системи.

Провери отделно: catalog snapshots + data recovery, monitoring и temporary disk quotas, storage egress, многопотребителска concurrency, compaction, rollback и version upgrades.

**Кога НЕ:** не въвеждай shared DuckLake/Quack само за еднократна справка; не приемай „по-евтино от cloud warehouse“ като измерена универсална истина. Нашият [локален JDBC lab](README.md) не претендира да тества тези услуги.
