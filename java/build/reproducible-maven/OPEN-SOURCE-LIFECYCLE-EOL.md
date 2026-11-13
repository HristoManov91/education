# P2 · Open Source Lifecycle — EOL, Backports, ELS и проверим software supply chain

> **Operational decision guide с изпълним P1 VEX companion lab**, без търговски софтуер или автоматично обновяване на dependencies. Източник: [Sven Sellen — Engineering for the Long Haul: Operating Open Source at Scale](https://www.youtube.com/watch?v=Pg5kS8l9CpI), Devoxx Belgium 2026, предоставен SRT (~32 мин; 07–13 мин upstream maintenance, 11–20 мин SecureChain/backport/VEX, 20–24 мин ELS). Лекторът е представител на **TuxCare**: SLA, coverage и performance са vendor claims, не независимо проверени гаранции.

## 1. Production казус — Critical CVE в EOL Java dependency

Spring Boot микросървис използва библиотека, която upstream вече не поддържа. Нашият deployed JAR е изграден с 1.2.x, докато текущият `main` използва друга версия. Изведнъж има CVE, но преминаването към latest major изисква промени в API, JPA модели, Oracle драйвер или клиентски интеграции.

**Bad:** „`mvn verify` на `main` е зелен, значи release-ът е безопасен“; или `trivy --ignore-unfixed` и ignore завинаги; или auto-merge major update без rollback. **Good:** inventory на **конкретните deployed bytes**, EOL/patch owner, ясно triage, проверен patch path, release evidence и решение кога да се оттегли legacy branch.

**Важно от лекцията:** upstream abandoned не означава, че няма техническа възможност за security backport; ELS не е равнозначно на бъдещи upstream feature updates; dependency patched не означава, че runtime използва patched variant.

## 2. Петимата кандидати за решение

| Избор | Кога | Кога НЕ | Печалба | Цена / рискове |
| --- | --- | --- | --- | --- |
| **Upgrade** до supported release | Има tested migration path | Нужна е голяма breakage миграция без validation | Най-малко продължаващ support debt | API/data migrations, performance/regression |
| **Backport** на конкретен fix | Знаем upstream patch, точен EOL fork и има reviewer | Няма patch lineage, security test или сигурен builder | Ограничена промяна без голям migration | Водим собствена patch линия, provenance и follow-up |
| **Extended Lifecycle Support (ELS)** | Нужна е временна поддръжка, vendor покрива точно OS/runtime/library и ABI | За да оправдаем безкрайна употреба на legacy | Специализирани fixes и документален trail | Разход, supplier lock-in, договорни ограничения |
| **Temporary mitigation** | Постижимо е реално намаляване на attack surface | Използва се вместо окончателна поправка | По-кратък риск прозорец | Partial coverage, expiry/recheck |
| **Retire / replace** | Функцията може да се премахне или замени | Няма migration/consumer plan | Изчезва нежеланата зависимост | Business и интеграционни разходи |

**Default:** upgrade към поддържан upstream release. Backport/ELS само с owner, expiry, доказателства и **exit plan**, не като нов permanent default.

## 3. Lifecycle inventory за дългогодишен Java продукт

За всеки *доставен* release, не само за `pom.xml`:

- **Identity**: Git tag/commit, JAR SHA-256, OCI image digest, JVM/OS версия, dependency versioned PURLs, SBOM serialNumber/version.
- **Support**: upstream LTS/EOL дата, vendor support matrix (OS/kernel/JDK/library), начална точка на SLA часовника, изключения от договора.
- **Security**: конкретно CVE/advisory, reachability, incident/patch owner, upstream diff, backport diff, tests, build provenance/signature, CVE re-evaluation.
- **Operations**: deployment/canary/rollback, readiness/SLO, последващо сканиране, incident response, дата на следващия audit.
- **End date**: коя supported версия заменя legacy fork, кой ще направи migration и кога.

**Status flow:** inventory → triage → affected / not_affected / false_positive → patch decision → rebuilt → tested → deployed → recheck. `under_investigation`/`in_triage` **не** е cleared.

## 4. Как се доказва backport

1. Идентифицирай vulnerability и vulnerable code path в **точния** upstream source.
2. Прегледай upstream security fix и разликата, която backport въвежда спрямо старата branch.
3. Провери, че patch-ът е приложен към *versioned fork*, build-нат от проверен source revision; без недоказани бинарни „drop-in fixes“.
4. Пусни regression/reproducer върху **build-натия и deploy-натия** artifact, не само mock/тест на новия `main`.
5. Подпиши artifact/attestation, валидирай signer identity и trust policy; документирай SBOM/PURL и, при нужда, VEX `resolved_with_pedigree`.
6. Увери се, че production работи с **този digest**, и създай recheck/expiry trigger.

[Нашата VEX & Backport Evidence лаборатория](../../../spring/testing/testcontainers/VEX-BACKPORT-EVIDENCE.md) показва deterministic consistency проверки и отрицателни сценарии. **Зелен резултат означава „готово за човешки преглед“, а не валидиран реален fix.** Нейните `CVE-2099-...`, patch и report са изрично синтетични.

## 5. Supplier evaluation: SecureChain, ELS и SLSA claims

Sven представя SecureChain, ELS, KernelCare и LibCare. Специфичните SLA числа и „complete CVE coverage“ са **търговски твърдения**, чиито условия трябва да се проверят за конкретния product/contract. Статистическите проценти от слайдовете нямат достатъчно описана методология в транскрипта, затова не ги използваме като security facts.

Checklist към доставчик:
- Кои package coordinates, OS/kernel, JDK ABI и архитектури се покриват? Какъв е support cutoff и кои CVE са изключени?
- Как се отчитат severity, часът на awareness/triage и действителното delivery SLA срещу „reasonable effort“?
- Има ли source/patch provenance, reproducible build evidence, подписан артефакт, проверима builder identity, лиценз и redistribution правно основание?
- Ако доставчикът заявява **SLSA Build L3**, къде са доказателствата за hardened build platform и signed provenance? Маркетингов текст **не е attestation**.
- Как изглежда rollback и exit migration обратно към supported upstream?

## 6. Практичен workflow — без нови зависимости

~~~bash
# Текущото Maven dependency tree НЕ е inventory на стар production release:
mvn -pl spring/testing/testcontainers dependency:tree

# Review на фиктивен release, backport и два VEX claims:
python3 spring/testing/testcontainers/vex-backport-evidence/verify_vex.py \
  --as-of 2026-11-13

# Очаквай negative tests да отхвърлят stale build/foreign BOM/in_triage:
python3 -m unittest discover \
  -s spring/testing/testcontainers/vex-backport-evidence -p 'test_*.py' -v
~~~

## 7. Decision contract

**Проблем:** дългогодишен OSS продукт/зависимост с security risk след upstream EOL.

**Кога:** legacy production, бъг с CVE, блокиран major upgrade и реален business cost.

**Кога НЕ:** когато обновяването към supported version е лесно — не въвеждай ELS/fork/paid vendor без причина.

**Плюсове:** контрол върху security lifecycle и audit; **минуси:** допълнителен CI, supplier/patch ownership, operational cost, риск от неверни „fixed“ claims.

**По-просто:** upgrade → test → signed release → immutable image → deploy/recheck.

**Proof:** [18 offline gate tests](../../../spring/testing/testcontainers/vex-backport-evidence/test_verify_vex.py), [Software Gardening](SOFTWARE-GARDENING.md), [CRA release evidence](../../../docs/security/cra/RELEASE-EVIDENCE-TEMPLATE.md).

За availability patch decision виж [Live Patching vs Rolling Update](../../../infrastructure/kubernetes/LIVE-PATCHING-VS-ROLLING-UPDATES.md). **Kernel live patch** няма да подмени Maven JAR в Java heap.

## Източници

- [Sven Sellen — Devoxx Belgium 2026](https://www.youtube.com/watch?v=Pg5kS8l9CpI), предоставен SRT (vendor viewpoint).
- [CycloneDX pedigree/backports](https://cyclonedx.org/use-cases/pedigree/), [CycloneDX BOM-Link/VEX](https://cyclonedx.org/guides/sbom/bom).
- [SLSA Build Track](https://slsa.dev/spec/v1.1/levels).
