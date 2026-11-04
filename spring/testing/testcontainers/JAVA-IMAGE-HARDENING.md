# P1 · Java Container Image Hardening: security, SBOM и доказуем supply chain

> **Източник:** [Pasha Finkelshtein — Mission Possible: The 45 Minute Path to Bullet Proof Java Container Images](https://www.youtube.com/watch?v=1yndLHw9mbo), Devoxx Belgium 2026 (00:00–42:55), предоставен SRT; допълва се от [Albert Attard — Production Ready Java Containers](https://www.youtube.com/watch?v=LPhGSgO5eKE). **Практическо разширение на Docker/Testcontainers**, не нова зависимост в Maven reactor. Използвай [Kubernetes Dockerfile](../../infrastructure/kubernetes/Dockerfile), [Deployment securityContext](../../infrastructure/kubernetes/manifests/30-deployment.yaml) и съществуващия [Dockerfile.bad](Dockerfile.bad).

## 1. Реалният казус — „образът се стартира, но кой го поддържа?“

В production работи Spring Boot service в container. През нощта scanner открива High CVE в OS библиотека. Разработчикът е променял само Java код, но притежава image-а. Да rebuild-не с `latest`? Да ignore-не unfixed? Да смени само base layer? Всяко действие има риск.

**Най-важната теза от лекцията:** търсим **нула неуправляван риск**, не абстрактно „нула CVE“. За всяко съществено откритие имаме **fix или документ за risk acceptance с owner, причина и дата на повторна проверка**. 46 и 135 CVE са временни числа от демонстрацията (август/октомври 2026), **не** текущи факти за конкретен image; scanner findings се променят.

## 2. Security layers: последователен bad → good

| Слой | BAD / проблем | GOOD / какъв риск намалява | Trade-off |
| --- | --- | --- | --- |
| Identity | процесът е root | `USER 10001:10001` + K8s `runAsNonRoot` | правилни файлови права за temp/data |
| Privileges | `--privileged`, escalation, всички capabilities | `allowPrivilegeEscalation:false`, `capabilities.drop: [ALL]`, seccomp | може да блокира legitimни native/syscall операции |
| Writable root | атакуващ може да промени runtime файлове | `readOnlyRootFilesystem:true`, явно `/tmp` emptyDir | конфигурирай writable dirs, persistence отделно |
| Build surface | JDK/Maven остава в production runtime | multi-stage build → само JRE + JAR | build image все пак трябва да се поддържа |
| Runtime surface | огромен Linux base без нужни инструменти | minimal/distro-less image или проверен `jlink` | по-труден live debug, CA/font/native зависимости |
| Artifact identity | mutable `:latest` tag | image **digest**, одобрен registry и проверена сигнатура | digest pinning изисква automation за patch updates |
| Contents | „нямаме CVE“ без evidence | SBOM за OS/runtime + application deps, scanning | scanner noise, false positives, discovery ≠ exploitability |
| Provenance | неизвестен builder/source | signed attestation, build identity, commit/digest relationship | keyless/OIDC policy, tooling, access controls |
| Response | без ownership или deadline | risk triage, SLA, owner, expiry/review; Renovate PR | постоянен maintenance капацитет |

**Non-root в Dockerfile НЕ замества Kubernetes SecurityContext**: едното е конфигурация на image-а, другото е policy на runtime. Използвай ги заедно. Не добавяй `NET_BIND_SERVICE` без нужда — приложението слуша на unprivileged `8080`.

## 3. Hands-on: сравни нашите два Dockerfile-а

```bash
# Старият базов курс показва разликата между root/baked secret и non-root:
cat spring/testing/testcontainers/Dockerfile.bad
cat spring/testing/testcontainers/Dockerfile
# Нов production-like multistage пример:
cat infrastructure/kubernetes/Dockerfile
mvn -pl infrastructure/kubernetes test
docker build -t education-kubernetes-lab:dev infrastructure/kubernetes
docker image inspect education-kubernetes-lab:dev --format '{{.Config.User}}'
# Очаквано: 10001:10001
```

[Multi-stage Dockerfile](../../infrastructure/kubernetes/Dockerfile) използва JDK 25 само за Maven compile/package, JRE 25 за изпълнение, exec-form ENTRYPOINT с Java PID 1 и отделен Pod SecurityContext. Docker tags са **демонстрационни mutable тагове**, не се преструваме, че са immutable. За production използвай проверени, **multi-platform image digests** със source/owner и автоматизирано предлагане на нови digest-и.

Защо не започваме от `FROM scratch`? Трябва правилно да разрешим native libraries, CA certificates, timezone/locale, fonts, DNS resolver, JFR и diagnostics. Минимизирай едва когато имаш smoke/regression доказателства. Не е вярно, че „по-малък image = нула опасни проблеми“.

## 4. SBOM, CVE scanner и supply-chain проверки — по реалния digest

Това са **manual инструменти**, които първо трябва да инсталираш и конфигурираш с реални доверени източници (Syft/Trivy/Cosign). Примерите **не се изпълняват от основния GitHub CI** и не заявяваме неподкрепени PASS резултати:

```bash
# Syft: преглед на runtime OS/JDK packages + Java dependencies
syft education-kubernetes-lab:dev -o cyclonedx-json=sbom.cdx.json

# Trivy: сканирай самия image, не само application dependencies
trivy image --severity HIGH,CRITICAL education-kubernetes-lab:dev

# Re-scan на запазения SBOM при нова vulnerability feed информация:
trivy sbom sbom.cdx.json

# Sign/verify са различни стъпки; НЕ приемай "verify ok" без trust policy:
# cosign verify --certificate-identity <approved-builder> \
#   --certificate-oidc-issuer <approved-issuer> registry.example/image@sha256:<verified-digest>
```

В реален release pipeline: build със pinned base, unit/IT → image smoke/architecture checks → SBOM от финалния image (OS и deps, отделно атрибутирани) → CVE triage → provenance + image signature → deploy by digest → periodic rescans → обновяване с PR + regression → audit trail. **SBOM сам по себе си не доказва, че бинарното съдържание е безопасно или неподменено.** Проверявай signature identity и provenance от доверен builder, не само съществуването на signature.

## 5. Правилна CVE triage семантика

**CVSS е severity за потенциален worst case, не вероятност за компрометиране на конкретно приложение.** В лекцията е дадено опростено статистическо сравнение, което НЕ трябва да се използва като вероятност „6% CVE → 6% шанс за пробив“. Запиши към всяка находка:

```text
CVE ID, компонент/версия, точен image digest
→ real reachability? (проверено с evidence, не assumption)
→ network exposure? privilege boundary? lateral movement?
→ exploit known? fix/version/backport available?
→ severity и compensating controls
→ action: patch now | scheduled patch | documented temporary acceptance
→ owner, next review date, due-by SLA, linked regression evidence
```

**Negative example:** `trivy --ignore-unfixed` и липсата на CVE в отчета НЕ доказват липса на риск. Няма patch? Нужни са owner, mitigations и повторна проверка. При публично достъпна и достижима от приложението уязвимост escalation може да е спешно, независимо от нисък score.

**За Spring4Shell:** презентацията използва конкретния експлоатационен път като илюстрация за контекст; не превръщай казаното „само WAR в отделен Tomcat“ в универсално твърдение за всички Spring4Shell variants и deployment configurations. Анализирай конкретната CVE, versions/advisories и условия за експлоатация.

## 6. Кой носи отговорността — build vs run vs patch

| Owner | Обичайна отговорност | Какво искаме като evidence |
| --- | --- | --- |
| App team | Java app + dependencies, tests, business configs | dependency SBOM, regression, safe rollout |
| Platform/base-image owner | OS/JDK/base image, registries, runtime standards | digest provenance, patch SLA, vendor support, rebuild process |
| Security | policy, triage workflow, exploit intelligence, exceptions | documented risk acceptance, reviews/expiry |
| Deployment team | admission controls, rollout, secrets, runtime policy | deployed digest, rollback, audit, monitoring |

Не предполагай, че всяка компания трябва да купува hardened-image vendor услуга. Собствен platform team или доставчик са легитимни варианти; сравни цена, patch response, trust boundary и възможност за independent verification.

## 7. EU CRA — правна точност

Лекторът споменава 11 декември 2027 и SBOM. Според Regulation (EU) 2024/2847 общите основни задължения започват **11 декември 2027**, а определени reporting задължения — **11 септември 2026**. **Не** приемай автоматично, че всеки Java микросървис или всяка компания е длъжна да предоставя SBOM на всеки клиент при поискване: приложимостта зависи от това дали продуктът попада в обхвата на CRA, ролята на производителя, вида на продукта и конкретните задължения. Това е технически обзор, не правно становище. За конкретен продукт провери нормативния текст и legal/compliance review.

## 8. Decision contract

- **Кога:** всяка Java услуга, която реално се разпространява като production image, има нужда от ownership и vulnerability process.
- **Кога НЕ:** не добавяй търговски scanner, подписване и отделен registry към учебно CLI без real supply-chain потребност. Поне non-root/multi-stage остават добра основа.
- **Печалба:** по-малка attack surface, идентичност на tested artifact, проверим risk response.
- **Цена:** scanning false positives, tooling, re-builds, trust-key policy, специализирана поддръжка.
- **По-просто:** малък trusted base + non-root + verified dependency updates + собствени тестове; добавяй supply-chain controls пропорционално на риска.
- **Proof:** мрежов [kind smoke](../../infrastructure/kubernetes/scripts/kind-smoke.sh), [offline negative security tests](../../infrastructure/kubernetes/scripts/test_validate_manifests.py), **manual** SBOM+scan+verification с действителни изходи.

## Оригинални източници

- [Devoxx Pasha Finkelshtein](https://www.youtube.com/watch?v=1yndLHw9mbo) и [Devoxx Albert Attard](https://www.youtube.com/watch?v=LPhGSgO5eKE), предоставени SRT.
- [Kubernetes Application Security Checklist](https://kubernetes.io/docs/concepts/security/application-security-checklist/).
- [SLSA supply-chain framework](https://slsa.dev/), [Sigstore Cosign verification](https://docs.sigstore.dev/cosign/verifying/verify/).
- [Syft SBOM](https://github.com/anchore/syft), [Trivy SBOM/CVE](https://trivy.dev/), [OCI Image Spec](https://github.com/opencontainers/image-spec).
- [Regulation (EU) 2024/2847 — CRA](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R2847), [European Commission CRA guidance](https://digital-strategy.ec.europa.eu/en/policies/cyber-resilience-act).
