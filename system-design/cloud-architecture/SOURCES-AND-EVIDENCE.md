# Cloud Architecture — source map and evidence

Проверено 2026-11-25. [Touret — Devoxx Poland 2026](https://www.youtube.com/watch?v=mOmuF5fDiUM), [официална Devoxx програма](https://devoxx.pl/talk/?id=6819), [оригинални slides](https://speakerdeck.com/alexandretouret/dvxpl26-pragmatic-multi-cloud-architecture-designing-for-resilience-and-portability-beyond-the-hype), [Touret — Part 1](https://blog.touret.info/2026/03/12/multi-cloud-from-the-trenches-part1/), [Touret — Part 2](https://blog.touret.info/2026/04/16/multi-cloud-from-the-trenches-part2/). Прочетен предоставен auto-generated SRT (~34:40). Не публикуваме transcript; timestamps приблизителни. Auto-caption греши в bounded context, BigQuery, Keycloak, introspection, FinOps. Slides са оригинален източник, лекцията остава основният текстов evidence.

| SRT range | Авторова тема | Наш материал |
| --- | --- | --- |
| 02–06 | Multi-cloud definition, best-of-breed, human cost, portability | CA-01/02 |
| 06–13 | Fleet, strategy, bounded contexts, facade, cross-provider calls | CA-01/02/03 |
| 13–16 | NFR, composite SLA simplification, compliance | CA-03/05 |
| 16–21 | SSO, claims, introspection, local keys | CA-05 |
| 21–25 | Source of truth, sync/async, split brain, user mapping | CA-05/06 |
| 25–30 | Performance, egress estimate, portability alternatives | CA-02/07 |
| 30–34 | Gateway/BFF, unified monitoring, design canvas | CA-08 |

## Primary sources (what they support)

- [Google Cloud — hybrid/multicloud patterns](https://docs.cloud.google.com/architecture/hybrid-multicloud-patterns-and-practices); [distributed patterns](https://docs.cloud.google.com/architecture/hybrid-multicloud-patterns-and-practices/distributed-patterns?hl=en) — partitioned vs redundant deployments, composite availability, connectivity.
- [AWS — recovery strategies](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel13-bp02.html); [DR planning](https://docs.aws.amazon.com/wellarchitected/latest/framework/rel13.html) — four recovery strategies, restore testing, failback, RTO/RPO. CA-04 is extension.
- [Kubernetes cloud controller](https://kubernetes.io/docs/concepts/architecture/cloud-controller/) — provider-specific integration.
- [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html); [Spring opaque token](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/opaque-token.html); [RFC 9068](https://www.rfc-editor.org/rfc/rfc9068); [RFC 7662](https://www.rfc-editor.org/rfc/rfc7662); [OpenID Connect Core §5.7](https://openid.net/specs/openid-connect-core-1_0.html#ClaimStability) — decoder/key discovery/cache, access-token claims, online introspection and identity stability.
- [AWS — transactional outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) — atomic local update/event, duplicate deliveries and ordering.
- [Google Cloud network pricing](https://cloud.google.com/vpc/network-pricing); [FinOps Foundation — unit economics](https://www.finops.org/framework/capabilities/unit-economics/) — directional charges and unit economics. No live price quote stored.
- [OpenTelemetry Collector deployment](https://opentelemetry.io/docs/collector/deploy/); [Google Cloud hybrid monitoring](https://docs.cloud.google.com/architecture/hybrid-and-multi-cloud-monitoring-and-logging-patterns?hl=en) — collector deployment and cross-environment telemetry.

## Corrections and evidence levels

Lecture's narrower definition is context-specific; minimum availability is upper bound; synchronous does not imply global consistency; async does not ensure no losses; local JWT requires claim validation and refresh/revocation policy; email not stable global identity; 260 GB only response direction in example, total 388.8 decimal GB with stated assumptions. No claim that PCI DSS requires a separate cloud or dedicated line. We don't reuse the talk's unverified survey percentages or historical network price estimate as facts.

All diagrams and labs are teaching adaptations, not original author code. No original runnable repo identified in supplied SRT. No AWS/GCP calls, live model/production data or actual provider recovery exercise. DR/exit/cost/compliance/observability case worksheets are design evidence, not executed production tests. Local network and signed JWT tests exercise actual implementation; in-memory outbox is only a deterministic model, real DB evidence in existing labs. JDK25/Boot4.1.1 match Education parent convention; CI execution status is recorded in PR, never invented. Future lectures should extend canonical CA chapters, add source/date/evidence and new tests or diagrams only when useful.
