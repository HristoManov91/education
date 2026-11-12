# P1 · EU Cyber Resilience Act (CRA) — от security risk до release evidence

> **Архитектурен/правен контекст + изпълними свързани лаборатории · актуализация: 12.11.2026.**
> Източник от Devoxx: [Christian Schabetsberger — CRAcking Compliance: The EU Cyber Resilience Act](https://www.youtube.com/watch?v=QQcsZNf0eWs), предоставен SRT (~45 мин). Точките от лекцията са сверени с регламента и актуалните материали на Европейската комисия; примерите за Java/Maven са наша адаптация. **Учебен материал, не правно становище и не декларация за съответствие.**

## 1. Реален казус: Spring Boot продукт с проблемна зависимост

Доставяме версия `2.3.0` на Java продукт с REST API, JAR, Docker image и 30 транзитивни зависимости. В петък откриваме нова CVE в библиотека, вградена в release-а. Нашият `main` branch вече използва друга версия, но клиентите имат стария image. Scanner-ът показва `CRITICAL`, докато екипът не знае:
- кои **конкретни издания и клиенти** са засегнати;
- дали опасният код може да бъде достигнат през нашето API;
- кои са отговорните лица, как да предоставим patch и какво да документираме;
- дали това е обикновена уязвимост, *активно експлоатирана* уязвимост или *тежък инцидент*, и дали CRA се прилага.

**Bad:** `mvn dependency:tree` върху текущия `main`, `trivy --ignore-unfixed` и „няма открити проблеми“. Това не идентифицира deployed release, не е vulnerability assessment и не изпълнява reporting obligations.

**Good:** установяваме product applicability → актуален threat model → SBOM за точния release → CVE triage → patch и regression → доказуем artifact/digest → комуникация и, *само ако има приложимо основание*, нормативно докладване.

Целта е да разберем **какво доказва всеки артефакт**, кой го поддържа и кога е нужно нещо повече от успешно сканиране.

## 2. Кога CRA се прилага — решаваме, не предполагаме

CRA е [Регламент (ЕС) 2024/2847](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R2847). Основният обхват са **продукти с цифрови елементи**, предоставяни на пазара на ЕС в рамките на търговска дейност; терминът включва при определени условия свързани **remote data processing solutions** (чл. 3, 2).

| Сценарий | Инженерна интерпретация | Следваща проверка |
| --- | --- | --- |
| Java продукт, който продаваме или лицензираме на клиенти в ЕС | Вероятно кандидат за обхвата | Кой е manufacturer, категория на продукта, дата/версия, роли по веригата, изключения |
| Вътрешна ИТ система, без продукт да се предоставя на пазара | Не приемаме автоматично, че всяка такава система попада в CRA | Бизнес модел, реално разпространение и приложимост на други закони/договори |
| SaaS / backend API | **Не** е „всяко SaaS е обхванато“ или „всяко SaaS е изключено“ | Дали е свързана remote data processing solution на предоставен продукт; прочети насоките на ЕК |
| Безплатен open-source проект | Няма автоматично освобождаване или автоматично задължение | Търговска дейност, monetisation, manufacturer vs open-source software steward |
| Библиотека в Maven Central | Нейният license не решава приложимостта на **нашия краен продукт** | Vendor/advisory, поддръжка, отговорен производител |

**Практическо правило:** преди compliance checklist попълни *product name, version, distribution model, role/manufacturer, intended use, support period, legal review owner*. Не маркирай „compliant“ само защото имаш SBOM или зелен CI.

Сверявай с [ЕК — обзор](https://digital-strategy.ec.europa.eu/en/policies/cra-summary), [ЕК — производители](https://digital-strategy.ec.europa.eu/en/policies/cra-manufacturers), [ЕК — open source](https://digital-strategy.ec.europa.eu/en/policies/cra-open-source) и [насоки на ЕК от 27.07.2026](https://digital-strategy.ec.europa.eu/en/library/commission-publishes-new-guidance-support-timely-cyber-resilience-act-implementation).

## 3. Ключови срокове и задължения, без объркване

| Дата | Какво се променя |
| --- | --- |
| **10.12.2024** | Регламентът влиза в сила |
| **11.06.2026** | Прилагат се разпоредбите за нотифициране на органи за оценяване на съответствието |
| **11.09.2026** | Чл. 14: задължения на производителите за докладване на активно експлоатирани уязвимости и тежки инциденти; важат и за продукти, предоставени преди общата дата |
| **11.12.2027** | Прилагат се основните изисквания; отделните правила за съществуващи продукти и съществени изменения имат нюанси; reporting за OSS stewards започва според съответните преходни правила |

**Чл. 14 — само ако продуктът и фактите пораждат reporting obligation:**

| Trigger | Ранно предупреждение | Основно уведомление | Окончателен доклад |
| --- | --- | --- | --- |
| **Активно експлоатирана уязвимост** в продукта | До **24 часа** от узнаването | До **72 часа** от узнаването | До **14 дни след налична коригираща/смекчаваща мярка**, не непременно след откриването |
| **Тежък инцидент**, влияещ върху сигурността на продукта | До **24 часа** от узнаването | До **72 часа** от узнаването | До **1 месец след подаване на 72-часовото уведомление** |

Това са срокове „без неоправдано забавяне“, с посочените крайни граници. Подаването е чрез [ENISA Single Reporting Platform](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting) и следва да се организира от упълномощената функция/лице.

**Опасно опростяване в лекцията (около 20–22 мин):** „всяка известна vulnerability → 24 часа“ и „severe incident = всяка неактивно експлоатирана vulnerability“. **Невярно**: обикновена CVE сама по себе си не задейства чл. 14; severe incident е **инцидент**, дефиниран с критерии в чл. 14(5). Security team първо квалифицира фактите и обхвата; не чака крайните срокове, ако вече има доказателства за злоупотреба.

Официални опори: [чл. 14](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R2847), [ЕК — Reporting obligations](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting).

## 4. Risk assessment / Threat Modeling — не документ веднъж преди одит

Според **чл. 13(2–3)** оценката на cybersecurity risks трябва да влияе на планиране, проектиране, разработка, производство, доставка и поддръжка, да бъде **документирана и актуализирана** според случая.

Четирите въпроса от лекцията (Adam Shostack): **Какво строим? Какво може да се обърка? Какво ще направим? Достатъчно добра ли е защитата?**

`Browser → Gateway → JWT validation → Spring API → Oracle` съдържа trust boundaries. Подмяна на `X-User-Id` е Spoofing; пропуск на resource ownership е Elevation of Privilege; неправомерен export е Information Disclosure. STRIDE подпомага систематичното откриване, но **STRIDE ≠ самостоятелно доказателство за съответствие**.

**Изпълним lab:** [Threat Modeling / STRIDE — Spring Security, bad/good, HTTP отрицателни тестове](../../../spring/security/threat-modeling/README.md). Пази diagram + решенията с owner и дата на преглед, а не само screenshot без контекст.

## 5. SBOM: inventory ≠ assurance

Annex I Part II(1) изисква идентифициране/документиране на уязвимости и компоненти, включително SBOM в общо използван machine-readable формат, **най-малко за top-level dependencies**. Това е нормативен минимум; инженерно често ни трябват и транзитивни, native и OS/JDK пакети, за да оценим release-а.

- **Direct Maven dependencies** ≠ цялото съдържание на JAR.
- `test` и `provided` dependencies може да не се разпространяват, но build/test toolchain също е отделен supply-chain риск.
- `SPDX` и `CycloneDX` са утвърдени стандарти; не прави необосновани изводи за качество само от броя редове.
- SBOM от **source graph** и SBOM от **финален image digest** имат различен scope. Съпостави резултатите.
- SBOM е част от техническата документация и не означава задължение да публикуваме пълната вътрешна dependency inventory на всеки клиент.

**Runnable offline accuracy lab:** [SBOM сравнителен експеримент](../../../spring/testing/testcontainers/SBOM-ACCURACY.md). **Production image hardening:** [Security, Syft, Trivy, provenance](../../../spring/testing/testcontainers/JAVA-IMAGE-HARDENING.md).

## 6. Continuous vulnerability handling — състояния и доказателства

`advisory/CVE → identify affected released digest → exposure/reachability assessment → remediation/mitigation → tests → release → periodic recheck`.

Нека всеки запис за уязвимост има:
- `product/version + BOM id + deployed digest`, CVE/advisory и конкретен компонент;
- доказателства за exposure, severity, known exploitation, available fix/backport;
- owner, deadline, remediation plan или **временно** приет остатъчен риск + review/expiry;
- verified patch version, regression tests, rescan, release communication;
- отделно решение „CRA reporting applicable? evidence?“ — никога не го извеждай само от CVSS.

За тези стъпки използваме текущите [CVE triage упражнения](../../../spring/testing/testcontainers/JAVA-IMAGE-HARDENING.md) и [Software Gardening / update policy](../../../java/build/reproducible-maven/SOFTWARE-GARDENING.md).

**Няма „нулев CVE“ като автоматична цел:** scanner може да показва false positives, а нулев scanner report не доказва липса на неизвестни уязвимости. Няма patch? Оцени compensating controls и последваща дата, не просто `--ignore-unfixed`.

## 7. Lifecycle, support, conformity

**Чл. 13(8):** периодът на поддръжка трябва да съответства на очаквания период на използване, обикновено **поне пет години**, освен ако обосновано очакваният живот е по-кратък; при по-дълъг очакван живот поддръжката може да трябва да е по-дълга. Security updates имат и отделни изисквания за продължителна достъпност (чл. 13(9)); не обърквай „налична за изтегляне“ с „нова поддръжка безкрайно“.

Изискват се подходящи техническа документация, инструкции и информация към потребителите, оценяване на съответствието, ЕС декларация за съответствие и CE маркировка **когато са приложими** (чл. 13, 28–32 и приложения). Не обещавай CE/compliance само на база нашата учебна лаборатория.

## 8. Реален release evidence pack

Шаблонът е [RELEASE-EVIDENCE-TEMPLATE.md](RELEASE-EVIDENCE-TEMPLATE.md), включва:
`source commit → reproducible JAR identity → image digest → BOM(scope) → scan findings/triage → tests → approver → deployment record → support & disclosure contact`.

За multi-module Maven:
`mvn -pl <module> -am dependency:tree` установява Maven resolution. CycloneDX `makeBom` е за module BOM, `makeAggregateBom` е за reactor aggregate и не означава автоматично, че всички пакети попадат във всеки runtime image. Готовите команди/fixtures са в [SBOM-ACCURACY](../../../spring/testing/testcontainers/SBOM-ACCURACY.md). [Reproducible Maven](../../../java/build/reproducible-maven/README.md) доказва build identity, не runtime safety.

## 9. Decision contract

| Избор | Проблем | Кога | Кога НЕ / trade-off | По-проста алтернатива / proof |
| --- | --- | --- | --- | --- |
| CRA legal applicability review | Грешни нормативни предпоставки | Продукт, който може да е на пазара на ЕС | Не е технически gate за всеки вътрешен API; юридически ресурс | Product inventory + legal review |
| STRIDE + risk register | Скрити trust boundary заплахи | Нова функция, интеграция, release с промяна на риск | Не прави тежък 100-страничен audit за учебен CLI | 4 въпроса + [HTTP tests](../../../spring/security/threat-modeling/README.md) |
| SBOM + reconciliation | Невидими транзитивни/OS компоненти | Доставени продукти и production images | Scanner noise, различни scopes, maintenance | `dependency:tree` + [fixtures/test](../../../spring/testing/testcontainers/SBOM-ACCURACY.md) |
| Dependency bot + gated upgrade | Наваксване на 6 major версии при CVE | Поддържани production системи | PR noise, CI цена, migration риск | Ръчни планирани small PR + [policy](../../../java/build/reproducible-maven/SOFTWARE-GARDENING.md) |
| Central vulnerability platform | История/видимост на много продукти | Десетки releases/services | Сървър, API keys, ownership и операции | Архивиран SBOM + triage таблица |

## 10. Изход — връщаме се към казуса

Намираме засегнатите **вече доставени** версии, документираме exploitability и owner, издаваме поправка със сравним identity, проверяваме внедрения digest и запазваме доказателствата. При **реално приложим trigger** legal/security owner стартира reporting flow, без да чака пълен postmortem. Поддръжката продължава и след следващия `main` commit.

**Mental model:** compliance не е scanner; това е доказуем, повторяем процес от архитектурен риск до поддръжка на клиента.

**Code/release review checklist:** product in scope? risk model updated? actual deployed artifact identified? complete enough BOM? reachable CVE? patch/regression? signature/provenance? release owner? disclosure policy? incident classification? next review date?

## Източници — нормативен текст пред презентацията

- [Regulation (EU) 2024/2847 — EUR-Lex](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32024R2847) — чл. 13/14/71, приложения I/II/VII.
- [European Commission CRA summary](https://digital-strategy.ec.europa.eu/en/policies/cra-summary) и [Reporting obligations](https://digital-strategy.ec.europa.eu/en/policies/cra-reporting).
- [Commission guidance 27 July 2026](https://digital-strategy.ec.europa.eu/en/library/commission-publishes-new-guidance-support-timely-cyber-resilience-act-implementation) — scope, remote data, substantial modifications, support, reporting.
- [Christian Schabetsberger — CRAcking Compliance](https://www.youtube.com/watch?v=QQcsZNf0eWs), SRT, особено 12–18 мин (risk), 18–32 мин (SBOM), 32–41 мин (scanning/gardening). Лекторът също изрично предупреждава, че не е юрист.
