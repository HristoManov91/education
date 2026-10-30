# Reproducible Builds & Build Integrity with Maven

> **P2 · Build Engineering / CI/CD / Software Supply Chain · Java 25 · Spring Boot 4.1.2 · Maven · JUnit**
>
> Източник: [Maarten Mulders — *Deterministic by Design: Achieving Reproducible Builds with Maven* (Devoxx 2026)](https://www.youtube.com/watch?v=9vtYvAI2Dtk). Материалът следва предоставения от потребителя автоматичен транскрипт; командите и детайлите за Maven са сверени с официалната документация. Това е **кратък практичен P2 модул**, не нова голяма Maven дисциплина.

## 1. Реален казус: „Същият commit, различен JAR“

В понеделник публикуваме `orders-api-1.5.0.jar` в production. В петък checkout-ваме **същия Git commit** за hotfix, но преди промяната искаме да възстановим същия release артефакт. Build-ът е зелен, тестовете минават, но SHA-256 хешовете на двата JAR файла се различават.

Това **не доказва дефект** в приложението, но означава, че не можем просто да приемем, че знаем какви точно bits сме доставили. При hotfix, одит или supply-chain инцидент това е проблем.

**Reproducible build:** при *същия source, build environment, build instructions и контролирани входове* получаваме **битово идентични** артефакти. Това е по-силно от „двата build-а са успешни“ или „тестовете минават“.

### Какъв проблем решава и кога има смисъл

| Решение | Кога го използваме | Кога НЕ / компромис |
| --- | --- | --- |
| Детерминирано JAR пакетиране | Production release, hotfix branch, OSS library, одити и incident response | За еднократен прототип може да е достатъчен по-лек build; все пак не използвай случайни версии на зависимости |
| Фиксирани зависимости и plugin versions (вкл. наследени от parent/BOM) | Когато искаме възстановим build при следваща версия на Maven/JDK | Не означава „никога не upgrade-ваме“ — Dependabot/Renovate с review |
| Локално `artifact:compare` | Бърз diagnostic gate за разлики в JAR | Не доказва независим rebuild в друга среда |
| Сравнение от два clean checkout-а | Контрол над stray files и различни working directories | Два checkout-а на един runner **не са** независима third-party атестация |

**Печелим:** проверим release процес, предвидими hotfix-и, по-добра диагностика и защита от някои build-time supply-chain подмени. **Губим:** време за настройка, допълнителен CI ресурс и необходимост да контролираме версиите, генерираните файлове и build средата.

## 2. Причини за различни binaries

| Източник | „Изглежда безобидно“ | Правилен подход |
| --- | --- | --- |
| JAR/ZIP timestamps | Всяко архивиране записва „сега“ | `project.build.outputTimestamp` + reproducible-capable plugins |
| Floating dependencies / ranges | `[2.0,3.0)` автоматично взима нов release | Pin version или managed concrete version чрез BOM |
| Maven plugins | Нямаме version в child POM | Проверяваме **effective POM** и наследения `pluginManagement`; не дублираме напразно parent versions |
| Build-time filtering | `maven.build.timestamp`, hostname, user, абсолютни пътища | Изключваме нестабилните входове от release binary; използваме runtime конфигурация |
| Local leftovers | Стари файлове от `target/` или untracked ресурси | `clean` и fresh checkout |
| Different toolchain | Различни JDK/Maven, OS, locale, architecture | Versioned toolchain/CI образ и инструкции за rebuild |

**Тънкост към октомври 2026:** според официалното Maven ръководство от **Maven 3.10.0 / 4.0.0-beta-5** reproducible mode е включен по подразбиране (Super POM). Това **не означава**, че всеки plugin и custom code вече са deterministic. Нашият lab задава фиксиран `outputTimestamp`, за да е ясен примерът и при по-стари Maven 3.x версии. Maven 4 все още има специфични migration нюанси.

## 3. README → конфигурация → код → доказателство

| Защо | Файл |
| --- | --- |
| Spring Boot parent, версия на Java, фиксиран timestamp, managed plugin versions и opt-in лош profile | [`pom.xml`](pom.xml) |
| Минимално реално Boot приложение за executable JAR | [`ReproducibleBuildApplication.java`](src/main/java/bg/hristomanov/education/build/ReproducibleBuildApplication.java) |
| Детерминиран UTF-8 serializer (не ползва часовник, среда, iteration order, OS newlines) | [`ReleaseMetadata.java`](src/main/java/bg/hristomanov/education/build/ReleaseMetadata.java) |
| Unit tests за положителни и отрицателни сценарии | [`ReleaseMetadataTest.java`](src/test/java/bg/hristomanov/education/build/ReleaseMetadataTest.java) |
| Контролиран антипример, **не е включен** в default JAR | [`build-time.properties`](src/demo/resources/build-time.properties) |
| Два fresh checkout-а и SHA-256 сравнение в GitHub Actions | [`reproducible-maven.yml`](../../../.github/workflows/reproducible-maven.yml) |
| Prompt за аудио повторение | [`NOTEBOOKLM-PROMPT.md`](NOTEBOOKLM-PROMPT.md) |

`ReleaseMetadata` е *допълнителен* детерминиран пример за генериране на metadata; **не** твърдим, че Maven автоматично извиква този клас по време на пакетиране. Реалното пакетиране се доказва чрез двата JAR build-а в CI.

## 4. Изпълними команди (Java 25, Maven, без Docker и credentials)

От root директорията на `education`:

```bash
mvn -pl java/build/reproducible-maven clean test
mvn -pl java/build/reproducible-maven clean package
mvn -pl java/build/reproducible-maven help:effective-pom -Doutput=target/effective-pom.xml
mvn -pl java/build/reproducible-maven org.apache.maven.plugins:maven-artifact-plugin:3.7.0:check-buildplan
```

В `target/effective-pom.xml` разгледай `maven-compiler-plugin`, `maven-resources-plugin`, `maven-surefire-plugin`, `maven-jar-plugin`, `spring-boot-maven-plugin` и техните **реално resolved** версии. Spring Boot parent вече ги управлява; не копирай версии произволно в child POM.

### A. Два build-а на една машина (първи smoke test)

**PowerShell / Windows:**

```powershell
$jar = "java/build/reproducible-maven/target/reproducible-maven-1.0-SNAPSHOT.jar"
mvn -pl java/build/reproducible-maven clean package
if ($LASTEXITCODE -ne 0) { throw "Build A failed" }
$first = (Get-FileHash $jar -Algorithm SHA256).Hash

mvn -pl java/build/reproducible-maven clean package
if ($LASTEXITCODE -ne 0) { throw "Build B failed" }
$second = (Get-FileHash $jar -Algorithm SHA256).Hash
if ($first -ne $second) { throw "Non-reproducible build" }
"PASS: two identical JAR files ($first)"
```

**Maven reference comparison** (първо инсталираме артефакта в локалното repository, след това проверяваме нов build):

```bash
mvn -pl java/build/reproducible-maven clean install
mvn -pl java/build/reproducible-maven clean verify org.apache.maven.plugins:maven-artifact-plugin:3.7.0:compare
```

При разлика разгледай `.buildcompare` и пусни `diffoscope` върху двата binaries (не интерпретирай hash difference като автоматично malicious code).

### B. Контролиран „лош“ build: clock leakage

Profile `demo-nondeterministic` е **изключен по подразбиране**. Само при активирането му Maven филтрира ресурс `generated.at=@maven.build.timestamp@`. Тази стойност е текущото build време, **различно** от фиксирания `project.build.outputTimestamp`. Така JAR съдържанието се променя, независимо че ZIP entry timestamps са контролирани.

```powershell
$jar = "java/build/reproducible-maven/target/reproducible-maven-1.0-SNAPSHOT.jar"
mvn -pl java/build/reproducible-maven -Pdemo-nondeterministic clean package
if ($LASTEXITCODE -ne 0) { throw "Bad build A failed" }
$first = (Get-FileHash $jar -Algorithm SHA256).Hash
Start-Sleep -Seconds 3
mvn -pl java/build/reproducible-maven -Pdemo-nondeterministic clean package
if ($LASTEXITCODE -ne 0) { throw "Bad build B failed" }
$second = (Get-FileHash $jar -Algorithm SHA256).Hash
if ($first -eq $second) { throw "Expected mismatch did not occur; inspect resource filtering" }
"EXPECTED: timestamps leak into the JAR; hashes differ"
```

Важно: ако plugin filtering не разреши property-то, сравняването може да остане зелено — затова CI **проверява и самия ресурс** в JAR, вместо да приема всеки различен hash за доказателство. В real project същият риск може да идва от `System.getenv()`, активен profile, generated sources, machine name или username.

### C. Два clean checkout-а (автоматичен CI gate)

[`Reproducible Maven Lab` workflow](../../../.github/workflows/reproducible-maven.yml) компилира същия commit **в две различни директории** на един GitHub runner с еднаква JDK 25/toolchain, сравнява JAR bytes чрез `cmp`, после демонстрира отрицателния clock-leakage profile.

**Граница на доказателството:** това е по-силно от последователни локални build-ове, но **не доказва independent third-party verification** — за такова доказателство трябва нова среда/друг оператор и проверка на публикувания release artifact. Не твърдим, че Reproducible Central е включен за този вътрешен lab.

## 5. Spring Boot нюанс: код ≠ packaged JAR

В Spring Boot `spring-boot-maven-plugin:repackage` създава executable JAR. Този plugin също има `outputTimestamp`, управляван по подразбиране чрез `project.build.outputTimestamp`. Ако използваш `spring-boot:build-info`, `build.time` по подразбиране използва същата стойност, ако е зададена; при липсваща може да използва start time на build сесията. Проверявай и custom plugin executions.

Reproducibility **не е**:
- test coverage, functional correctness или security scanning;
- гаранция, че dependency-ите са без уязвимости;
- подпис/произход на артефакта или immutable deployment;
- доказателство, че production действително изпълнява точно този JAR — отделно ни трябват deploy digest, provenance и change tracking.

**Алтернативи:** при малък вътрешен проект започни с pinned dependencies, Maven Wrapper и clean CI release; при high-assurance pipeline добави независим rebuild, checksum verification against deployed artifact, SBOM и attestation. Не превръщай всеки commit build в скъп release verification без бизнес основание.

## 6. Упражнения и проверка на разбирането

1. Изпълни `clean package` два пъти и сравни SHA-256. Какво доказва това и какво **не**?
2. Активирай `-Pdemo-nondeterministic`, виж `build-time.properties` в JAR и обясни защо `outputTimestamp` не решава проблема.
3. Разгледай `effective-pom.xml`. Откъде идват версиите на `maven-jar-plugin` и `spring-boot-maven-plugin`?
4. Обясни защо version range на runtime dependency е риск, когато JAR съдържа transitive dependencies.
5. Предложи release gate за hotfix на стар commit, без да rebuild-ваш „latest main“.

**Senior-level въпрос:** двата JAR са битово идентични, но по време на deploy се инжектира грешен secret. Това build-provenance, runtime configuration или application-security проблем ли е? Какви независими доказателства са нужни?

## 7. Изход: какво запомняме и кога НЕ използваме

При началния hotfix казус вече имаме **проверим модел**: фиксираме входовете, компилираме от clean checkout, сравняваме байтовете, преглеждаме разликите и съхраняваме digest за публикувания артефакт. Ако SHA-256 е различен, диагнозата започва от timestamps, dependency/plugin resolution, generated resources и средата.

**Mental model:** *same source + controlled build inputs → same bytes*; *same bytes ≠ secure code*; *local compare ≠ independent attestation*.

**Code-review checklist:** concrete versions/BOM? effective plugin versions? stable output timestamp? build-time env leaks? clean checkout? pinned JDK/Maven? comparison of release binaries? verified deploy digest? Не налагай скъп процес, ако за проекта не е оправдан.

## Оригинални източници

- [Devoxx — Maarten Mulders, Deterministic by Design](https://www.youtube.com/watch?v=9vtYvAI2Dtk) — мотивация, causes, Maven `artifact` goals, Reproducible Central и security boundaries; адаптирано от потребителския transcript.
- [Apache Maven — Configuring for Reproducible Builds](https://maven.apache.org/guides/mini/guide-reproducible-builds.html) — official steps, Maven 3.10+ defaults, known limits.
- [Maven Artifact Plugin — `check-buildplan` и `compare`](https://maven.apache.org/plugins/maven-artifact-plugin/plugin-info.html) — официални цели, comparison files.
- [Maven Plugin Version Configuration](https://maven.apache.org/guides/mini/guide-configuring-plugins.html).
- [Spring Boot Maven Plugin — executable JAR `outputTimestamp`](https://docs.spring.io/spring-boot/maven-plugin/packaging.html).
- [Spring Boot Maven Plugin — `build-info` time](https://docs.spring.io/spring-boot/maven-plugin/build-info.html).
- [Reproducible Builds project](https://reproducible-builds.org/) — независими rebuilds и threat model.
