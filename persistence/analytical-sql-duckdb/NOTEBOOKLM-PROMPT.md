# NotebookLM — DuckDB: From Laptop to Lakehouse (Java 25/JDBC)

**Първичен източник:** [Oliver Geisser — From Laptop to Lakehouse](https://www.youtube.com/watch?v=B5-4mDLrWjM), Devoxx 2026, предоставен SRT, ~29 мин. В auto-transcript има „duct DB“, „duck leg“ и „quark“ — правилните термини са **DuckDB, DuckLake, Quack**. Не приписвай на лектора нашите Java25/JUnit примери.

**Допълнителни Education източници:** [DuckDB аналитичен SQL lab](README.md), [TrafficAnalytics.java](src/main/java/bg/hristomanov/education/duckdb/TrafficAnalytics.java), [12 JUnit сценария](src/test/java/bg/hristomanov/education/duckdb/TrafficAnalyticsTest.java), [DuckLake/Quack manual decisions](MANUAL-LAKEHOUSE.md), [Hardwood Case 005](../../docs/case-studies/005-hardwood-ai-parquet-engineering.md), [QueryDSL](../specification-querydsl/README.md). Официални: [JDBC](https://duckdb.org/docs/2.0/clients/java/overview), [DuckDB 2.0 release, 21.10.2026](https://duckdb.org/2026/10/21/announcing-duckdb-200), [DuckLake 1.1](https://ducklake.select/docs/stable/).

Направи **30–40 минути български аудио урок за Senior Java/Spring backend developer**, с пример за синтетични GPS събития, които се записват в OLTP Oracle/Postgres, но историческите отчети се изпълняват върху изолиран CSV/Parquet export. Не твърди, че ползваме live системни данни или реално мигрираме Oracle.

Върви по логиката на лекцията: CLI `duckdb` (in-memory/file) → Java JDBC и row-based `ResultSet` срещу columnar chunks → CSV autodetection с **leading-zero identifier** проблем → Parquet direct scans и larger-than-memory tradeoffs → remote HTTPS/S3 и persistent secrets → SQLite/PostgreSQL federation → DuckLake (SQL catalog + Parquet object storage) → Quack protocol.

**Критична актуализация:** при презентацията Quack e alpha/бъдещ, но към 15.11.2026 **DuckDB 2.0.0 LTS** вече е пуснат и включва Quack и `CONNECT`. **DuckLake ≠ DuckDB ≠ Quack**. Cloud/Quack/Postgres са manual архитектурни продължения, без автоматични integration tests.

Обясни изпълнимите **12 JUnit теста**: `jdbc:duckdb:`, директна CSV агрегация, exact decimal/PreparedStatement, запазване на `00001` като String, Parquet `COPY`, `read_parquet`, `row_number()`, `EXPLAIN`, in-memory срещу persistent, invalid input и overwrite rejection. Нашата фикстура има **10 фалшиви GPS точки**; лекцията показва по-голяма демонстрация, но не измисляй измервания върху нашия проект.

За всяка тема: **проблем, кога да използвам, кога НЕ, печалби, загуби (CPU/I/O/RAM/latency/privacy/ops), по-проста алтернатива, работещ пример и тест**. Сравни DuckDB с SQL в Oracle/Postgres, explain vs explain analyze, projection/predicate pushdown без неверни ускорения и streaming/chunked JDBC vs row iteration без изкуствени benchmark числа. Покрий risks: untrusted SQL/file access, secret encryption/storage, extension trust, network egress, inconsistent federated snapshots и disk spill quota.

Завърши с 10 Senior questions без готови отговори и checklist за одобрен Parquet export → DuckDB SQL → резултат/данни/доказателства. **Не твърди**, че имаме работещ production lakehouse или Quack сървър.
