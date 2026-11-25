# Event-Driven Consistency — exercises

| ID | Самостоятелен казус |
| --- | --- |
| EC-01 | Order + stock one DB, email external. Choose axes and alternative. |
| EC-02 | Payment times out, stock reserved. Draw known rejection vs unknown; optional notification fails. |
| EC-03 | Receipt commits, ACK lost, worker throws after SQL. Predict rows and run tests. |
| EC-04 | v1 observed 10:02, v2 10:01, v2 delivered first. Explain timestamp trap, delta vs snapshot. |
| EC-05 | v1 fails 3 times and v2 waits. Plan redrive and alert. |
| EC-06 | DB first/Kafka first: run broker proof. Compare EOS vs XA vs Saga. |
| EC-07 | Roll back source; then fail listener after SQL, restart and recover. |
| EC-08 | Design failure matrix + operational runbook for order/payment/loyalty. |

След собствен отговор: local rollback != external; timeout != rejection; receipt != effect; stable ID + version; gap requires recovery; DLT != success; prepare/commit != compensation; registry still requires idempotency. No mastery scores auto-assigned.

[Lab](lab/README.md) · [Kafka lab](kafka-lab/README.md) · [Sources](SOURCES-AND-EVIDENCE.md).
