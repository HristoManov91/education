# Cloud Architecture — exercises & diagnostic cases

Първо отговори сам; после провери критерии. ID CA-01–08 са extension, не автоматично оценени микро-теми.

| ID | Казус | Задача |
| --- | --- | --- |
| CA-01 | One service, two providers, same DB | Избери topology според business goal. Посочи shared dependencies. |
| CA-02 | BigQuery query/credential в AWS service | Начертай bad/good facade; exit inventory и migration rehearsal. |
| CA-03 | Mandatory 99% + 95%; analytics optional | Изчисли availability при independent failures и bounds без independence; покажи stale/unavailable contract. |
| CA-04 | Region loss, payment RPO=0 | Напиши DR runbook с fencing, RTO/RPO validation, corruption restore, failback. |
| CA-05 | IdP unreachable, known JWT, new kid, wrong audience | Предскажи резултат; стартирай JWT tests; обясни why not email linkage. |
| CA-06 | Partition, duplicate, delete, old event | Предскажи projection и pending count; пробвай lab; различи snapshot vs delta. |
| CA-07 | 100 RPS, 500 B/1000 B, 30 days | Изчисли по посоки; повтори при 20% retries, estimate fixed + variable. |
| CA-08 | Unified gateway, central telemetry down | Начертай failure paths; define SLIs and local visibility. |

## Capstone: Fleet architecture review

Дадени учебни, не production изисквания: customer journey 99.9%, p95 300 ms, отчет freshness до 15 min, payment non-duplicate effect, tracking RTO 4h/RPO 15min, residency policy TBD. Изработи C4 context/container и deployment (provider/region/zone), две sequence diagrams (success/outage), data ownership table, IAM/trust boundary, SLO/DR plan, cost worksheet, exit ADR. Сравни single cloud/multi-region/partitioned multi-cloud. Explain one simpler alternative and what invalidates choice. Няма универсално правилен вариант; unanswered residency е blocker, не предположение.

## Критерии след самостоятелен отговор

01: topology ≠ DR; shared fate. 02: semantic contract и measured exit, not generic SDK wrapper. 03: 94.05% independent, bounds 94–95%; stale not fresh, auth fail-closed. 04: actual RTO/RPO, fencing, backup≠replica, resync. 05: signature + claims/type, known key only while usable, unknown fails, issuer+subject. 06: owner keeps events until ACK, duplicate/reorder handling, tombstone, in-memory≠durable. 07: 129.6/259.2 GB, 388.8 total, actual prices not example. 08: optional reporting, gateway HA, telemetry not mandatory request dependency.

[Lab](lab/README.md) · [System Design assessment](../ASSESSMENT-CASES.md) · [Оригинални източници](SOURCES-AND-EVIDENCE.md).
