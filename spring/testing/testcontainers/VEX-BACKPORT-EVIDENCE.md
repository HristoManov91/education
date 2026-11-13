# P1 · VEX & Backport Evidence — CycloneDX 1.6 практическа лаборатория

> **Реална offline Python 3 stdlib лаборатория, 18 положителни/отрицателни теста и синтетични CycloneDX 1.6 документи.** Източник: [Sven Sellen — Engineering for the Long Haul](https://www.youtube.com/watch?v=Pg5kS8l9CpI), предоставен SRT (~11–20 мин: supplier patch pipeline, source rebuild, VEX/SBOM/SLSA, ELS). Изложените услуги SecureChain и ELS са **търговски предложения на TuxCare**, не задължителни open-source стандарти. Тук изграждаме собствен проверим Java-oriented казус.

## 1. Казус: scanner намира CVE, но fork има backport

Spring Boot продукт `orders-api 1.5.0` съдържа версия `pkg:maven/edu.example/legacy-parser@1.2.0-edu1`. За нея получаваме две твърдения:

- **`CVE-2099-90001`** — *измислен* CVE, за който е заявен `resolved_with_pedigree`: backport diff и security issue в component pedigree.
- **`CVE-2099-90002`** — *измислен* CVE, за който е заявен `not_affected` поради `code_not_reachable`.

Тези идентификатори НЕ съществуват като реални advisory записи и **не** доказват поправка на реална уязвимост. Целта е да проверим дали твърденията са за **точния release, компонент и версия**.

**Bad:** отчитаме `not_affected` като универсален allowlist за всички версии на `legacy-parser`, или `resolved_with_pedigree` без доказуем patch. **Good:** всяко твърдение има конкретни scope, owner, доказателства и срок за повторен преглед.

## 2. CycloneDX VEX и BOM-Link

Идентичността на статичния SBOM е:

~~~text
serialNumber: urn:uuid:11111111-1111-4111-8111-111111111111
version: 1
component bom-ref: legacy-parser-fork-1.2.0-edu1

VEX affects.ref:
urn:cdx:11111111-1111-4111-8111-111111111111/1#legacy-parser-fork-1.2.0-edu1
~~~

Отделният VEX документ може да се актуализира без промяна в инвентара на продукта. Това следва препоръчания модел за **CycloneDX BOM-Link**. Bare `bom-ref` без точен BOM identifier не е безопасен заместител за cross-document linkage.

| Концепция | Стандартен CycloneDX 1.6 | Изрично допълнение в нашия review gate |
| --- | --- | --- |
| Product | `metadata.component.{name,version,hashes}` | SHA-256 на конкретните байтове в синтетичния release artifact |
| Maven dependency | `components[].{bom-ref,purl,pedigree}` | Версиониран PURL + уникална identity |
| Backport | `pedigree.patches[].{type,diff,resolves}` | `type=backport`, base64 diff, точно security issue ID и проверен patch hash |
| Vulnerability scope | `vulnerabilities[].affects[].ref` | BOM-Link към конкретното SBOM UUID, version и component ref |
| Remediated | `analysis.state=resolved_with_pedigree` | Owner, diff, regression evidence hash, review expiry |
| Unreachable | `analysis.state=not_affected` и `justification=code_not_reachable` | Детайлно обяснение, scoped reachability report, owner и expiry |
| Неясен статус | `analysis.state=in_triage` | **FAIL: никога не приемаме „неизследвано“ за безопасно** |

**OpenVEX е различен формат:** използва `affected/fixed/not_affected/under_investigation`. Нашият код чете **CycloneDX `analysis.state`**, а не OpenVEX; не смесвай двете enum множества.

## 3. README → изпълним код → доказателства

| Материал | Цел |
| --- | --- |
| [verify_vex.py](vex-backport-evidence/verify_vex.py) | Fail-closed local evidence consistency check; exit `0` за READY_FOR_HUMAN_REVIEW, `2` за REVIEW_BLOCKED |
| [test_verify_vex.py](vex-backport-evidence/test_verify_vex.py) | 18 теста, включително чужд BOM-Link, грешен release SHA, дублирани PURL/ref, изтекъл review, непотвърден patch, `in_triage` |
| [release.cdx.json](vex-backport-evidence/fixtures/release.cdx.json) | Синтетичен SBOM с component pedigree |
| [vex.cdx.json](vex-backport-evidence/fixtures/vex.cdx.json) | Отделен синтетичен CycloneDX VEX с 2 измислени CVE |
| [review.json](vex-backport-evidence/fixtures/review.json) | **Наш local review contract, не стандартно CycloneDX поле** |
| [backport.diff](vex-backport-evidence/fixtures/backport.diff), [regression-report.txt](vex-backport-evidence/fixtures/regression-report.txt), [reachability-report.txt](vex-backport-evidence/fixtures/reachability-report.txt) | Byte-level evidence fixtures, **не** истински патчван JAR/реален тестов run |

От repository root (няма Maven/JDK/scanner dependency):

~~~bash
python3 -m unittest discover \
  -s spring/testing/testcontainers/vex-backport-evidence \
  -p 'test_*.py' -v

python3 spring/testing/testcontainers/vex-backport-evidence/verify_vex.py \
  --fixtures spring/testing/testcontainers/vex-backport-evidence/fixtures \
  --as-of 2026-11-13
~~~

Очаквано: `READY_FOR_HUMAN_REVIEW` — **НЕ** `SAFE`, `NOT_AFFECTED_VERIFIED`, `SLSA_ATTESTED` или release approval.

### Break-it exercise

Смени UUID в `vex.cdx.json` на чужд SBOM; `review.json` да остане непроменен. Очаквай `REVIEW_BLOCKED` и exit 2. Повтори със сменен byte в `backport.diff`; липсващ `analysis.justification`; `state=in_triage`; `expires_at=2026-11-12`. Тестовете показват *защо* конкретните проверки са необходими, не само че успешният пример е зелен.

## 4. Какво **не** доказва offline gate-ът

- SHA-256 consistency **не удостоверява произход**. Злонамерен издател може да подмени artifact, BOM, VEX, local evidence и всичките им хешове.
- `regression-report.txt` е синтетичен текст, **не доказателство за изпълнен тест или действително приложен patch**.
- Нямаме уязвим real Java JAR, signed supplier provenance, възпроизводим trusted build, проверен signer, подписан VEX или production reachability analysis.
- Скриптът проверява **нашия ограничен review policy subset**, не е универсален CycloneDX JSON Schema validator, vulnerability scanner, CVSS calculator или legal/CRA certification.
- Отчетът `not_affected` е *твърдение на owner*, което изисква независимо evidence; не може да се извежда само от версия или нисък CVSS.

Production pipeline: schema validation със съответния CycloneDX version; **trusted source commit + patch lineage + binary provenance/signature**, тестове на реалния patched артефакт, проверен deployed digest, vulnerability reachability review, security approval, expiry и повторно сканиране.

## 5. Decision contract

**Какъв проблем?** Версионният scanner и реалният backport статус на **точен artifact** могат да се различават.

**Кога?** Legacy fork, supplier backport или ясна reachability assessment с конкретен owner.

**Кога НЕ?** Когато upstream upgrade е лесен, няма подписани/проверени build proofs или CVE е все още `in_triage`.

**Какво печелим?** Изрична release → component → CVE → evidence връзка, по-малко false-positive suppression, управление на срока на изключението.

**Какво губим?** Review процес, поддръжка на BOM/VEX, regression/attestation tooling, риск от неверни твърдения.

**По-просто:** Upgrade към supported upstream, rebuild на image и rescan на deployed digest.

Виж [Software Gardening](../../../java/build/reproducible-maven/SOFTWARE-GARDENING.md), [EOL strategy](../../../java/build/reproducible-maven/OPEN-SOURCE-LIFECYCLE-EOL.md), [CRA release evidence](../../../docs/security/cra/RELEASE-EVIDENCE-TEMPLATE.md).

## Оригинални източници

- [Sven Sellen — Devoxx 2026](https://www.youtube.com/watch?v=Pg5kS8l9CpI), предоставен SRT (~16–20 мин).
- [CycloneDX 1.6 JSON Schema](https://cyclonedx.org/schema/bom-1.6.schema.json), [официален BOM-Link](https://cyclonedx.org/guides/sbom/bom), [CycloneDX pedigree](https://cyclonedx.org/use-cases/pedigree/).
- [OpenVEX specification](https://github.com/openvex/spec/blob/main/OPENVEX-SPEC.md), [SLSA Build Track](https://slsa.dev/spec/v1.1/levels).
