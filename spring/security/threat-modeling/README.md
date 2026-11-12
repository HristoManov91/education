# P1 · STRIDE Threat Modeling за Spring Boot микросървиси

> **Java 25 · Spring Boot 4.1.1 / Spring Security 7 · изпълним HTTP/JUnit lab.**
> Източник: [Christian Schabetsberger — CRAcking Compliance](https://www.youtube.com/watch?v=QQcsZNf0eWs), 12–18 мин, предоставен SRT. STRIDE и четирите въпроса са *общи техники за security engineering*, не ексклузивни правила на CRA. [CRA контекст](../../../docs/security/cra/README.md).

## 1. Реалният казус — валиден JWT, чужди данни

Работим по backend зад API Gateway:

`Browser → API Gateway (TLS/JWT) → Spring Boot /api/records/{owner} → Oracle`

Gateway потвърждава самоличността, но frontend е добавил header `X-User-Id` „за удобство“. Разработчикът на контролера вярва на този header или проверява **само** `authenticated()`. Тогава потребител `bob` може да прочете `/api/records/alice`. Друг разработчик прибавя `ROLE_REVIEWER` проверка, но забравя, че role ≠ object ownership. По-късно CSRF се изключва глобално, въпреки че API-то използва браузърно изпращан HTTP Basic.

**Симптоми:** успешен 200 за чужд запис, approval без собственост, request headers които имитират identity, липса на доказателство, че POST има CSRF защита.

**Ще научиш:** trust boundaries, STRIDE, рискове и controls, разлика между AuthN, coarse-grained AuthZ и object-level AuthZ, отрицателни HTTP тестове, документируем security decision. Няма да представяме този пример като реален JWT gateway, Oracle repository, durable audit или DoS защита — те са разширения.

## 2. Четирите въпроса на Adam Shostack

1. **What are we building?** Начертай актьорите, данните, системите и **границите на доверие**.
2. **What can go wrong?** Прегледай всяка trust boundary с STRIDE.
3. **What will we do?** Намали, прехвърли или съзнателно приеми риска — писмено, с owner/преразглеждане.
4. **Did we do a good enough job?** Докажи контролите с тестове и преразгледай модела при API/auth/deploy промени.

**Не започвай от инструмент за диаграми.** Достатъчни са 30 минути бяла дъска + запазен diagram, заплаха, owner, тест.

```text
Actor: alice/bob (untrusted browser)
    |
    | username/password + user-controlled HTTP headers
    v [BOUNDARY A: Internet → API]
Spring Security filter chain   <-- authentication + role rules
    |
    v [BOUNDARY B: actor → record ownership]
RecordAccessPolicy             <-- business object authorization
    |
    v [BOUNDARY C: controller → persisted data, NOT in this lab]
Oracle / service integration
```

В тази лаборатория контролерът връща синтетичен отговор **без persistence**. Реалната система има и други boundaries: JWT signer, encryption at rest, database permissions, secret store, audit stream, rate limits.

## 3. STRIDE матрица: от заплаха до доказателство

| Категория | Какво може да се обърка | Контрол / текущ lab | Какво все още НЕ е доказано |
| --- | --- | --- | --- |
| **S**poofing | `X-User-Id: alice` от bob | Използваме проверения principal, не request header; [HTTP test](src/test/java/bg/hristomanov/education/threatmodel/ThreatModelHttpTest.java) | JWT `iss/aud/exp`, gateway-to-service header authentication |
| **T**ampering | Client променя `owner` или role header | Object policy + отрицателни тестове, `ROLE_REVIEWER` не идва от `X-Roles` | Payload signing, external message integrity |
| **R**epudiation | Няма кой е одобрил запис | **Gap:** ще изисква durable, защищен audit с request ID, actor, timestamp | Не сме реализирали immutable/durable audit |
| **I**nformation disclosure | GET чужд запис | [RecordAccessPolicy](src/main/java/bg/hristomanov/education/threatmodel/RecordAccessPolicy.java) отказва; 403 test | Маскиране на PII, log redaction, DB row-level policies |
| **D**enial of service | Request flood / heavy queries | **Gap:** rate limit, bounded work, quotas, timeouts са future controls | Нямаме DoS test; Spring Security authorization не е rate limiter |
| **E**levation of privilege | ROLE_REVIEWER без ownership одобрява чужд запис | URL role + domain ownership; separate tests | Реално approval workflow, SoD, database authorization |

STRIDE е систематичен **brainstorming инструмент**; няма автоматично едно-към-едно юридическо доказателство за всяка точка от Annex I. За всяка заплаха документирай **asset → attack precondition → impact → probability/assumptions → mitigation → residual risk → owner → evidence**.

## 4. README → код → proof

| Решение | Къде | Как го доказваме |
| --- | --- | --- |
| Servlet-level AuthN + endpoint role + deny-all | [SecurityConfiguration.java](src/main/java/bg/hristomanov/education/threatmodel/SecurityConfiguration.java) | [ThreatModelHttpTest.java](src/test/java/bg/hristomanov/education/threatmodel/ThreatModelHttpTest.java) |
| Object-level ownership / anti-spoofing | [RecordAccessPolicy.java](src/main/java/bg/hristomanov/education/threatmodel/RecordAccessPolicy.java) | [RecordAccessPolicyTest.java](src/test/java/bg/hristomanov/education/threatmodel/RecordAccessPolicyTest.java) |
| GET/POST boundaries / negative outcomes | [RecordController.java](src/main/java/bg/hristomanov/education/threatmodel/RecordController.java) | [HTTP tests](src/test/java/bg/hristomanov/education/threatmodel/ThreatModelHttpTest.java) |
| Изолирани credentials само локално | [DemoUsers.java](src/main/java/bg/hristomanov/education/threatmodel/DemoUsers.java) | [IntelliJ requests](http/records.http), не production credentials |
| Build & runner | [pom.xml](pom.xml), [ThreatModelApplication.java](src/main/java/bg/hristomanov/education/threatmodel/ThreatModelApplication.java) | `mvn -pl spring/security/threat-modeling test` |

## 5. Bad → root cause → good

**На пръв поглед разумен bad вариант:**

```java
@GetMapping("/{owner}")
Map<String, String> read(@PathVariable String owner,
                         @RequestHeader("X-User-Id") String claimedActor) {
    // Gateway вече е логнал потребителя — защо да проверяваме повторно?
    return recordStore.read(owner); // BUG: нито claimedActor, нито owner имат доказана връзка
}
```

Root cause: **authentication ≠ authorization**, а client-supplied identity е недоверена. Дори да добавиш `claimedActor.equals(owner)`, bob може да подаде `X-User-Id: alice` и да заобиколи логиката.

**Good:** [контролерът](src/main/java/bg/hristomanov/education/threatmodel/RecordController.java) получава Spring Security `Authentication`, [policy-то](src/main/java/bg/hristomanov/education/threatmodel/RecordAccessPolicy.java) изисква `owner.equals(authentication.getName())` и при approval — `ROLE_REVIEWER`. [Филтърната верига](src/main/java/bg/hristomanov/education/threatmodel/SecurityConfiguration.java) има `denyAll()` fallback; CSRF остава включен, защото HTTP Basic може да се изпраща автоматично от браузъра. API Gateway не отменя проверката във downstream услугата.

**Важна граница:** MockMvc `user(...)` е **симулиран валиден principal**; тестовете доказват HTTP authorization logic, **не** криптографската валидност на token, TLS, gateway и IDP. За production JWT resource server добави отрицателни тестове за issuer/audience/expiry/signature и header trust policy.

## 6. Стартирай лабораторията

```bash
# От root на education: изпълни 4 policy unit теста + HTTP Spring Security тестове
mvn -pl spring/security/threat-modeling test

# За локален HTTP experiment с умишлено демо credentials:
mvn -pl spring/security/threat-modeling spring-boot:run -Dspring-boot.run.profiles=local-demo

# Anonymous request → 401; не са необходими credentials:
curl -i http://localhost:8080/api/records/alice

# demo alice / local-demo-only → 200 за собствен record
curl -i -u alice:local-demo-only http://localhost:8080/api/records/alice

# bob не може да се представи за alice дори с header → 403
curl -i -u bob:local-demo-only -H 'X-User-Id: alice' http://localhost:8080/api/records/alice
```

[Готови IntelliJ HTTP заявки](http/records.http) — `{{basicAlice}}` и `{{basicBob}}` са локални environment variables с Base64 credentials; не ги commit-вай като тайни. Публикуваните имена/пароли са **само учебни**, профилът `local-demo` никога не е за deployment.

<details>
<summary>Примерни резултати (без стартирано приложение)</summary>

```text
GET /api/records/alice (anonymous)              -> HTTP 401
GET /api/records/alice (alice)                  -> HTTP 200
{"owner":"alice","status":"visible"}
GET /api/records/alice (bob, X-User-Id: alice)  -> HTTP 403
POST /api/records/alice/approve (alice reviewer, no CSRF) -> HTTP 403
POST /api/records/alice/approve (alice reviewer + CSRF in MockMvc) -> HTTP 200
{"owner":"alice","result":"approved"}
```

</details>

`POST` примера умишлено не е успешен в чистия `curl`/IntelliJ flow без CSRF токен; [MockMvc тестът](src/test/java/bg/hristomanov/education/threatmodel/ThreatModelHttpTest.java) го симулира правилно с `csrf()`. Не отключвай `csrf.disable()` само за красив зелен demo резултат.

## 7. Decision contract — кога използваме STRIDE и къде не стига

| Въпрос | Практически отговор |
| --- | --- |
| Проблем | Непроверени допускания за identity/ownership зад gateway |
| Кога | Нов endpoint с права, интеграция, API / permission migration, deployment change, риск при release |
| Кога НЕ | Не е смислено да генерираме 100 страници за еднократно CLI без външен input; но базов risk discussion остава полезен |
| Печалби | Ясни boundaries, negative tests, ownership и traceability |
| Цена | Review време, поддръжка на диаграми, непълнота, false sense of security ако не се актуализира |
| По-просто | 4 въпроса + една страница диаграма; добави STRIDE при по-рискови boundaries |
| Proof | HTTP status **и** policy unit assertions — не само успешен startup |

**Не твърдим**, че toy approval response доказва audit integrity, DB correctness, rate limiting, replay resistance или целия CRA compliance процес.

## 8. Практическо упражнение и изход

1. Махни `owner.equals(authentication.getName())` от policy и провери кой **отрицателен тест** става червен; възстанови проверката.
2. Позволи approval само по role — кой test открива Broken Object Level Authorization?
3. Добави реален `GET /api/records/{owner}/history` и започни с negative test за bob.
4. Начертай отделна граница за Gateway → API: кой има право да създаде вътрешен identity header? Какво ще се случи при директен обход на gateway?
5. Разшири risk register с immutable audit, секрети, DoS, replay, logging и запиши кое е *untested*.

**Връщаме се към началния казус:** bob с валидна authentication, но чужд record, получава 403. Роля без ownership не позволява approval. Фалшиви identity/role headers са игнорирани. Останалите STRIDE gaps са изрично записани, вместо да се представя лабораторията за „безопасна по подразбиране production система“.

**Code-review checklist:** actor identity proven? headers trusted? object id bound to actor? global route deny? CSRF/session semantics? negative 401/403/200 tests? audit evidence? rate limit? residual risk owner?

## Източници

- [OWASP Threat Modeling](https://owasp.org/www-community/Threat_Modeling) и [OWASP API Security Top 10 — BOLA](https://owasp.org/API-Security/editions/2023/en/0xa1-broken-object-level-authorization/).
- [Microsoft STRIDE overview](https://learn.microsoft.com/en-us/azure/security/develop/threat-modeling-tool-threats).
- [Spring Security Authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html) и [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
- [Devoxx — Christian Schabetsberger](https://www.youtube.com/watch?v=QQcsZNf0eWs), предоставен SRT; [EU CRA](../../../docs/security/cra/README.md).
