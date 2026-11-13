# P1 · SBOM Accuracy: Maven dependencies ≠ packaged Java application ≠ OCI image

> **Runnable offline lab (Python 3 stdlib, 5 unit tests) + manual Maven/Syft/Trivy commands.** Разширение на [Java Container Image Hardening](JAVA-IMAGE-HARDENING.md), [EU CRA](../../../docs/security/cra/README.md) и [Reproducible Maven](../../../java/build/reproducible-maven/README.md).
> Източник: [Christian Schabetsberger — CRAcking Compliance](https://www.youtube.com/watch?v=QQcsZNf0eWs), около 18–35 мин, предоставен SRT. Angular/npm/.NET comparison е **преведен към Java/Maven**; демонстрационните числа от лекцията НЕ са измерване върху Education.

## 1. Реален казус

При release на Spring Boot image имаме `pom.xml` с 12 директни зависимости, `mvn dependency:tree` показва 100+, CycloneDX plugin генерира повече записи, а Syft от финален image вижда още OS/JDK пакети и различен брой Java библиотеки. Мениджърът пита „кой инструмент е счупен?“. Отговорът е, че **различна инвентаризация и различен scanner scope** не доказват сами по себе си дефект.

**Bad:** сравняваме `components.length` и приемаме най-малкото число за по-„чисто“ и най-голямото за по-„пълно“. Така може да пропуснем runtime транзитивен JAR, дори директната зависимост да е налична.

**Good:** определяме **кой точно release (JAR SHA/image digest)** сравняваме, избираме runtime components за проверка с човешки преглед и проверяваме **component identities** (PURL с версия), не само броя. Отчитаме излишните и липсващите записи; не смесваме build/test dependencies с OS packages.

## 2. Карта: README → код → проверка

| Защо | Файл |
| --- | --- |
| Компоненти по versioned PURL; fail on missing expected | [compare_sboms.py](sbom-accuracy/compare_sboms.py) |
| Положителен/отрицателен тест, version identity, duplicate, malformed input | [test_compare_sboms.py](sbom-accuracy/test_compare_sboms.py) |
| Reviewed runtime PURLs | [expected-runtime.json](sbom-accuracy/fixtures/expected-runtime.json) |
| Изходен Maven BOM със source+test dependency | [maven-source.cdx.json](sbom-accuracy/fixtures/maven-source.cdx.json) |
| „Грешно“ сканиране без транзитивен компонент | [image-missing-transitive.cdx.json](sbom-accuracy/fixtures/image-missing-transitive.cdx.json) |
| „Добро“ сканиране + OS package | [image-complete.cdx.json](sbom-accuracy/fixtures/image-complete.cdx.json) |

Всички `example/*` PURL-и са **измислени фикстури**, не действителни CVE или packages в Maven Central. Те не зависят от мрежа, scanner версия или GitHub credentials.

## 3. Изпълни положителния и отрицателния тест

```bash
python3 -m unittest discover -s spring/testing/testcontainers/sbom-accuracy -p 'test_*.py' -v

# PASS: source graph и release image съдържат двата review-нати runtime PURL
python3 spring/testing/testcontainers/sbom-accuracy/compare_sboms.py \
  --source spring/testing/testcontainers/sbom-accuracy/fixtures/maven-source.cdx.json \
  --release spring/testing/testcontainers/sbom-accuracy/fixtures/image-complete.cdx.json \
  --expected spring/testing/testcontainers/sbom-accuracy/fixtures/expected-runtime.json

# ОЧАКВАН FAIL exit code 2: един транзитивен runtime component липсва
python3 spring/testing/testcontainers/sbom-accuracy/compare_sboms.py \
  --source spring/testing/testcontainers/sbom-accuracy/fixtures/maven-source.cdx.json \
  --release spring/testing/testcontainers/sbom-accuracy/fixtures/image-missing-transitive.cdx.json \
  --expected spring/testing/testcontainers/sbom-accuracy/fixtures/expected-runtime.json
```

Положителният тест дава `missing_in_release: 0`, но `unreviewed_in_release: 1` (OS пакет); отрицателният дава `missing_in_release: 1` за `pkg:maven/example/transitive-lib@2.0.0`. **Не го представяй като доказателство за пълнота на SBOM** — сравняваме само прегледаните PURL-и.

## 4. От fixture към real Java/Maven pipeline (ръчна лаборатория)

Инсталирай предварително Maven, JDK 25, Docker, Syft и Trivy с проверен произход. От root на `education`:

```bash
# Maven dependency resolution за избрания module:
mvn -pl spring/security/threat-modeling dependency:tree \
  -DoutputFile=target/dependency-tree.txt

# CycloneDX schema/версия се фиксира изрично; plugin версията НЕ е взета от Maven parent:
mvn -pl spring/security/threat-modeling \
  org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom \
  -DoutputFormat=json

# За агрегирана инвентаризация на reactor използвай makeAggregateBom;
# тя не означава, че всеки runtime image съдържа ВСИЧКИ reactor зависимости.
# mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -DoutputFormat=json

# От действително build-нат image — не смесвай tag с immutable identity:
# syft registry.example/application@sha256:<verified-digest> -o cyclonedx-json=release.cdx.json
# trivy image registry.example/application@sha256:<verified-digest>
```

**Внимание:** `2.9.3` е конкретен пример за pin-нат plugin, а не обещание за най-нов release. Периодично провери валидната версия и каква CycloneDX schema поддържа, преди да избереш output за съответен downstream tool. Не добавяме CycloneDX/Trivy към всеки default CI build; тук са **опционални manual проверки**.

С реално генерираните два BOM-а: разгледай кой scanner е включил Maven `test`/`provided` компоненти, native libraries и OS/JDK. Ръчно review-ни очакваните runtime PURL-и от **финалния артефакт**, не просто от `pom.xml`, и използвай script-а за focused coverage проверка. Ако source/plugin ползва `bom-ref` без PURL или OS пакет няма PURL, този прост lab **няма да го съпостави**; допълни parser-а/format contract-а според реалния инструмент, вместо да пропускаш silently.

### P1 следваща стъпка: VEX и версиониран BOM-Link

[**VEX & Backport Evidence**](VEX-BACKPORT-EVIDENCE.md) започва **след** доказана inventory identity: отделен CycloneDX 1.6 VEX свързва fictitious CVE към exact `serialNumber/version#bom-ref` и versioned Maven PURL. Има 18 negative/positive теста за stale release, missing patch, чужд BOM, not_affected justification и under-investigation. Това е **consistency review**, не криптографски signed provenance и не разрешение за scanner suppression.

## 5. Защо резултатите могат да се различават

| Причина | Симптом | Как да проверя |
| --- | --- | --- |
| Direct vs transitive | POM има 12, BOM има 100+ | `mvn dependency:tree` и effective POM |
| `test/provided/optional` scopes | Maven BOM съдържа неща, които ги няма в image | Прегледай dependency scopes и packaged JAR |
| Base OS + JDK | Image scanner показва повече компоненти | Отдели `pkg:maven` от OS/JDK PURL |
| Maven exclusions/shading | Source dependency не съвпада с release bytes | Разгледай `BOOT-INF/lib`, relocated classes, native libs |
| Platform/version aliases | Пакетите имат различно namespacing/PURL qualifiers | Нормализирай scheme + namespace + name + version; съхрани provenance |
| Непълен scan / scanner error | Липсва очаквано runtime transitive package | Не приемай „по-малко“ за по-сигурно; run negative tests |
| New CVE feed | Един и същ SBOM днес има други findings от вчера | Архивирай scanner/feed timestamp и revisit |

Нашият toy comparator игнорира PURL qualifiers и subpath (но **запазва версията**), deduplicate-ва точно идентични PURL-и и отчита extras като предупреждение. Това не е production-grade parser за всички PURL edge cases; използвай специализиран parser/format validation при реален compliance pipeline.

## 6. CVE scanners — не ги бъркай със SBOM генератори

| Инструмент | Роля | Компромис |
| --- | --- | --- |
| Maven `dependency:tree` | Maven resolution inspection, **не** machine-independent SBOM | Няма OS/JDK инвентаризация |
| CycloneDX Maven Plugin | Maven-level CycloneDX BOM | Не доказва image contents |
| Syft | Анализ на filesystem/JAR/image packages и SBOM | Discovery варира според formats и scanner version |
| Trivy / Grype | Vulnerability scanning + package inventory (според command) | DB freshness, CVE matching, false positives |
| OWASP Dependency-Check | SCA за зависимости; локални HTML отчети | Feed updates, CPE false positives и build-time |
| Dependency-Track | Централизирана история на BOM-и и vulnerability monitoring | Separate service, API tokens, persistent storage/ops |

Ако имаш 2 малки вътрешни услуги, `dependency:tree` + проверен SBOM/scan в release process може да е достатъчно. За десетки releases и одитна история Dependency-Track може да оправдае operational cost. Не инсталираме нова платформа по подразбиране.

## 7. Decision contract и изход

- **Проблем:** невидима транзитивна зависимост на deployed image, въпреки зелен Maven build.
- **Кога:** release inventory, supply-chain incident response, CRA product lifecycle, image upgrades.
- **Кога НЕ:** не изисквай огромен централен scanner за еднократно локално упражнение; запази минималната инвентаризация.
- **Печалби:** reproducible сравнение по identity, открита липса, прегледна traceability.
- **Цена:** пълнотата на expected списъка изисква човешки review, parser limitations, scanner noise, време за security operations.
- **По-просто:** Maven dependency tree + преглед на финалния JAR + ръчно записан идентификатор.
- **Доказателство:** [5 offline теста](sbom-accuracy/test_compare_sboms.py) + ръчни Syft/CycloneDX стъпки; **не** твърдим, че Syft/Trivy са изпълнявани в default CI.

**Връщаме се към казуса:** не сравняваме „423 vs 555“ като рейтинги, а питаме дали конкретният `pkg:maven/...@version` трябва да присъства в конкретен release. Ако липсва, разследваме coverage и действителния artifact; не маркираме продукта „secure“ само по зелен comparator.

Източници: [CycloneDX Maven plugin](https://cyclonedx.github.io/cyclonedx-maven-plugin/), [Syft](https://github.com/anchore/syft), [Trivy](https://trivy.dev/), [Dependency-Track](https://dependencytrack.org/), [EUR-Lex CRA Annex I Part II](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R2847), [Devoxx запис](https://www.youtube.com/watch?v=QQcsZNf0eWs).
