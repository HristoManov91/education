# CRA / Software Supply Chain — примерен release evidence pack

> **Учебен шаблон** за продукт, при който applicability е **отделно проверена**. Сам по себе си не е доказателство за правно съответствие, certification или CE mark. Не commit-вай production client data, PII, tokens или private CVE details в публично repository.

## A. Identity & ownership

- Product / release / intended use:
- Manufacturer / product owner / legal assessment link:
- CRA applicability: `In scope / Out of scope / Undetermined` + основание, оценил, дата:
- Support period + аргументи по чл. 13(8):
- Product release commit/tag:
- JAR SHA-256 / SBOM digest / OCI digest:
- Builder provenance, signer и проверена trust identity:
- Deployed environment/digest и дата на deployment:

## B. Risk assessment

- Threat model version / diagram / trust boundaries:
- Assets и external actors:
- STRIDE threats, severity, въздействие и компенсиращи мерки:
- Owner и дата на следващия преглед:
- Известни ограничения и **остатъчни рискове**, одобрени от owner:

## C. Vulnerabilities / software gardening

| ID | Affected artifact/digest | Reachability / evidence | Fix/mitigation | Owner + due date | Rescan + regression | Review |
| --- | --- | --- | --- | --- | --- | --- |
| SAMPLE-NOT-A-REAL-CVE | `sha256:example` | Неизследвано — не се приема за safe | Планиран patch | TODO | TODO | TODO |

- SBOM tool/version/schema + scope (Maven vs packaged JAR vs image OS/JDK):
- VEX document digest + CycloneDX BOM-Link to affected SBOM serialNumber/version/component:
- VEX state/justification vs backport pedigree, source diff, actual deployed binary proof and signer validation:
- EOL / ELS supplier coverage, support expiry, verified SLA clock + exit-to-supported-version plan:
- Known gaps and false-positive decisions (без `ignore all`):
- Dependency bot PR, update delay policy, breaking change review:
- Unit / integration / HTTP security test links:
- Rollback plan, user communication, security update availability:

## D. Incident / reporting decision — **НЕ се автоматизира от CVSS**

- Manufacturer and product within CRA scope? evidence:
- **Actively exploited vulnerability?** known facts, first awareness timestamp (timezone):
- **Severe incident affecting product security?** ch. 14(5) analysis:
- Classification/approval by security + legal owner:
- If applicable: 24h, 72h и final-report deadlines calculated **according to the actual trigger**:
- ENISA SRP submission references (restricted records only):
- Coordinated disclosure plan / customer remediation / postmortem:

## E. Release approval

- Approver & timestamp:
- Release tested equals artifact deployed?:
- Evidence expiry/recheck date:
- Known open exceptions:
- Last check against [EUR-Lex](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R2847) and [Commission guidance](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting):

**Граници:** проходил security scan ≠ безопасен продукт; `SBOM generated` ≠ completeness; `reproducible JAR` ≠ signature of deployed image; технически owner ≠ правен decision-maker.
