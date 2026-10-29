# Bootiful Security — Spring Security 7 от login до BFF и защитен MCP

> **Devoxx Belgium 2026 · P1 · Spring / Security · Java 25 · Spring Boot 4.1.1**
>
> Видео: [Bootiful Security — Daniel Garnier Moiroux & Josh Long](https://www.youtube.com/watch?v=W-OKVLMtPAw) (~51 минути). Материалът следва предоставения SRT transcript; кодът е наша учебна адаптация, а не копие на сценичния пример.

## 1. Реалният казус

Имаме backend с обикновени потребители и администратори. Всеки може да се идентифицира, но само някои могат да изпълняват чувствителни операции. Един ден се появяват още 20 microservices, React SPA, централен Identity Provider (IdP) и AI агент, който извиква MCP tools от името на потребителя.

**Bad подход:** всяка услуга има отделна users таблица, собствен login, паролите са хеширани с SHA-256, а JavaScript държи access token. За endpoint сме добавили `hasRole("ADMIN")` и сме приели, че това е достатъчно.

**Какво може да се обърка:** пропуснат endpoint става публичен, offline password guessing става евтин, открадната session се използва за администраторска операция, OAuth token попада в browser logs или XSS, MCP agent получава твърде широки права.

**Цел:** да различаваш identity, permissions, authentication factors и token trust boundaries; да докажеш първия слой със Spring Security HTTP тестове и да знаеш как надграждаш към OAuth2/OIDC, BFF и MCP.

## 2. Карта на оригиналната лекция

| Минути | Демонстрация | Какво учим |
| --- | --- | --- |
| 05–07 | AuthN/AuthZ, secure defaults | Principal, SecurityFilterChain, deny by default |
| 07–12 | Hashing и upgrade при login | bcrypt, Argon2id, DelegatingPasswordEncoder |
| 13–16 | One-Time Tokens | OTT vs TOTP, out-of-band delivery |
| 17–22 | Passkeys / WebAuthn | private/public key, challenge, phishing resistance |
| 22–27 | Multi-Factor Authentication | ADMIN + password factor + OTT factor |
| 27–34 | Authorization Server, OAuth Client, Resource Server | ID token, access token, issuer/JWT |
| 35–40 | RestClient, interceptor, HTTP interfaces | token propagation |
| 40–45 | SPA, Gateway / Backend for Frontend | server-side token handling, TokenRelay |
| 46–50 | Spring AI + MCP | tools, delegated OAuth user authorization |

## 3. Senior mental model

```text
Caller proves identity (Authentication)
    -> Authentication state: business roles + verified factor authorities
    -> AuthorizationManager: may this caller do THIS action?
    -> business endpoint

Distributed system:
Browser -> OAuth Client / BFF ----access token----> Resource Server
                |                                         |
            session cookie                      check iss, aud, exp, scopes
                |
           Authorization Server / OIDC Provider
```

- **AuthN** установява *кой* е caller-ът. **AuthZ** решава *какво може да прави*.
- `ROLE_ADMIN` и `FACTOR_OTT` са различни неща: първото е business permission, второто е доказателство за authentication method.
- **ID token** служи за identity към OAuth/OIDC client. **Access token** се представя на resource server според неговия audience и scopes. Подписан JWT сам по себе си НЕ означава достъп.
- Browser session cookie не е access token и не оправдава изключване на CSRF.

## 4. Naive → root cause → good: три изпълними урока

### A. Secure by default

**Bad:** `/api/admin` е защитен, но забравяме `/api/internal-export`.

**Good:** [SecurityConfiguration.java](src/main/java/bg/hristomanov/education/bootifulsecurity/SecurityConfiguration.java) задава ясни граници: `/api/public` е публичен, `/api/me` изисква authentication, `/api/admin/**` изисква ADMIN + step-up MFA, а `anyRequest().denyAll()` затваря всичко останало.

**Важен нюанс:** нашият собствен `SecurityFilterChain` замества default Boot chain, така че **трябва изрично да запазим нужните protection-и**. Spring Security 7 поддържа и композиционни `Customizer<HttpSecurity>` beans; това е друг подход, при който се надгражда конфигурацията, а не непременно се заменя цялата chain.

### B. Password hashing и миграция

**Bad:** SHA-256 е бърз, което помага на атакуващ да прави огромен брой offline guesses след leak на password database.

**Good:** adaptive salted hash — Argon2id (OWASP предпочита при подходящи параметри) или bcrypt. В този **изпълним lab** Spring `DelegatingPasswordEncoder` използва bcrypt, за да не изискваме допълнителна native/Bouncy Castle интеграция. Префиксът `{bcrypt}` позволява различните алгоритми да съществуват паралелно.

`matches(raw, encoded)` проверява парола, `upgradeEncoding(encoded)` казва дали трябва rehash. Ако потребител влезе успешно, системата **може** да хешира с по-силна конфигурация и да запише с `UserDetailsPasswordService`. Това не става магически за всички съществуващи акаунти без plaintext. [PasswordHashTest.java](src/test/java/bg/hristomanov/education/bootifulsecurity/PasswordHashTest.java) доказва солене, проверка на парола и разпознаване на legacy bcrypt cost.

**Корекция на опростяване в лекцията:** SHA-1 collisions (примерът SHAttered) демонстрират промяната на криптографските алгоритми, но причината **SHA-256 да не е подходящ за password storage е неговата скорост**, не наличието на известна collision атака.

### C. Step-up MFA за рисковите операции

**Bad:** щом сесията има `ROLE_ADMIN`, позволяваме чувствителна операция.

**Good:** `AuthorizationManagerFactories.multiFactor()` изисква **и** `FACTOR_PASSWORD`, **и** `FACTOR_OTT`, **и** `ROLE_ADMIN`. Обикновеното `/api/me` не изисква step-up. Така не объркваме authentication strength с business permission.

One-Time Token (OTT) е server-issued single-use credential, доставен по out-of-band канал; **не е TOTP/HOTP** код от authenticator app. [OneTimeTokenServiceTest.java](src/test/java/bg/hristomanov/education/bootifulsecurity/OneTimeTokenServiceTest.java) демонстрира забраната на replay.

[LocalDemoTokenDelivery.java](src/main/java/bg/hristomanov/education/bootifulsecurity/LocalDemoTokenDelivery.java) показва token само на конзолата с изричен `local-demo` profile. Това **не е production delivery и не доказва независим втори фактор**: реално е нужен външен канал, TTL, throttling, защита от enumeration, storage и recovery процедура.

## 5. Passkeys / WebAuthn — защо са предпочитани пред магическите линкове

При регистрация сървърът запазва публичния ключ, а private key остава при потребителя/неговия authenticator. При login сървърът изпраща challenge и проверява криптографския подпис. Fingerprint/Face ID отключва ключа локално, **биометричният шаблон не пътува до приложението**.

WebAuthn е phishing-resistant поради origin/RP binding (при правилна конфигурация). За production са необходими правилни `rpId`/`allowedOrigins`, TLS, registration, recovery и политика за изгубени устройства. Официалната [Passkeys документация](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html) покрива `spring-security-webauthn` и registration/authentication endpoints.

**Граница на този модул:** WebAuthn е архитектурно обяснен, но НЕ е обявен за реализиран и автоматично тестван. Реалното удостоверяване изисква browser/authenticator integration, която MockMvc user setup не доказва.

## 6. README → код → proof

| Концепция | Код | Тест |
| --- | --- | --- |
| Spring Boot entry point | [BootifulSecurityApplication](src/main/java/bg/hristomanov/education/bootifulsecurity/BootifulSecurityApplication.java) | [HTTP suite](src/test/java/bg/hristomanov/education/bootifulsecurity/BootifulSecurityHttpTest.java) |
| PasswordEncoder, users, URL rules | [SecurityConfiguration](src/main/java/bg/hristomanov/education/bootifulsecurity/SecurityConfiguration.java) | [PasswordHashTest](src/test/java/bg/hristomanov/education/bootifulsecurity/PasswordHashTest.java) |
| HTTP responses and Principal | [DemoController](src/main/java/bg/hristomanov/education/bootifulsecurity/DemoController.java) | [BootifulSecurityHttpTest](src/test/java/bg/hristomanov/education/bootifulsecurity/BootifulSecurityHttpTest.java) |
| Dev-only OTT delivery | [LocalDemoTokenDelivery](src/main/java/bg/hristomanov/education/bootifulsecurity/LocalDemoTokenDelivery.java) | [OneTimeTokenServiceTest](src/test/java/bg/hristomanov/education/bootifulsecurity/OneTimeTokenServiceTest.java) |
| Loopback binding | [application.properties](src/main/resources/application.properties) | manual browser check |
| OAuth2/BFF/MCP design | [OAUTH2-BFF-MCP.md](OAUTH2-BFF-MCP.md) | [EXERCISES.md](EXERCISES.md) |

## 7. Стартиране — единствено на локална машина

Трябват JDK 25, Maven и свободен порт 8080. Няма нужда от база, Docker, SMTP, IdP или платена услуга.

```bash
# От root на education:
mvn -pl spring/security/bootiful-security test

# Стартиране изрично с опасния само-за-обучение delivery profile:
mvn -pl spring/security/bootiful-security -Dspring-boot.run.profiles=local-demo spring-boot:run
```

Отвори `http://127.0.0.1:8080/api/public` и `http://127.0.0.1:8080/api/me`. Учебни credentials (НЕ за реални системи):

| User | Password | Roles |
| --- | --- | --- |
| `alice` | `local-demo-password` | USER |
| `admin` | `local-demo-password` | USER, ADMIN |

След login с `admin` отвори `/api/admin/report`. Липсващият OTT фактор задейства step-up login. При `Generate` токенът се изписва в **локалната конзола**, оттам го подай във формата. Профилът `local-demo` е задължителен; без delivery bean приложението отказва startup (fail-closed). `server.address=127.0.0.1` не позволява достъп от външна мрежа.

## 8. Тестовият oracle и ограниченията му

- Anonymous може да достъпи само позволения public endpoint; за профила се връща login redirect.
- USER чете своя profile; ADMIN **без** потвърдени фактори НЕ получава admin report.
- ADMIN + двете факторни authorities получава 200; USER + фактори, но без ADMIN, получава 403.
- За POST запазваме CSRF и дори успешна MFA не го заобикаля.
- Непознат endpoint остава забранен; грешна парола не осигурява login.
- OTT е one-time; второ използване е invalid.

**Тестовите фактори в MockMvc се симулират**, за да проверим authorization правилото. Това **не** доказва истинска email delivery, passkey signature, independent factors, cookie lifecycle или OAuth end-to-end.

## 9. Надграждане към останалата половина на лекцията

[OAuth2, OIDC, BFF/Token Relay и OAuth-protected MCP](OAUTH2-BFF-MCP.md) описва **дизайн и конфигурационни фрагменти**, а не невидими работещи services. [Exercises](EXERCISES.md) дава насочващи въпроси и постепенно изграждане на външен IdP/BFF.

## 10. Production/code-review checklist

- Знам ли кой е caller-ът, как е удостоверен и каква *конкретна* операция е разрешена?
- Има ли explicit public endpoints + `denyAll` fallback? Няма ли рискована промяна на matcher order?
- Използвам ли adaptive salted password hash, правилни параметри и реално persistent upgrade при login?
- Различавам ли roles, OAuth scopes, audience и фактическите `FactorGrantedAuthority`?
- Запазена ли е CSRF защита за session/cookie browser requests?
- Не излизат ли пароли, magic links, access tokens или refresh tokens в production logs?
- Проверявам ли issuer, audience, signature, expiry, scopes и token type в resource server?
- При SPA остават ли access/refresh tokens на backend-а и имаме ли secure HttpOnly SameSite session cookies?
- При AI/MCP има ли per-user delegated permissions, per-tool authorization и защита от prompt injection?
- Има ли интеграционни тестове за real login, OTT replay, consent, logout и role changes?

**Изход:** authentication доказва identity; authorization решава достъп до действие; допълнителните фактори не заменят roles; OAuth token е валиден само в правилната trust boundary.

## Оригинални източници

1. [Bootiful Security — Josh Long & Daniel Garnier Moiroux, Devoxx Belgium 2026](https://www.youtube.com/watch?v=W-OKVLMtPAw). Потребителският SRT е използван тематично; не се публикува дословно.
2. [Daniel Garnier Moiroux — списък с лекции](https://garnier.wf/talks).
3. [Spring Security — Java configuration](https://docs.spring.io/spring-security/reference/servlet/configuration/java.html).
4. [Spring Security — Passwords](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/).
5. [Spring Security — One-Time Token Login](https://docs.spring.io/spring-security/reference/servlet/authentication/onetimetoken.html).
6. [Spring Security — Passkeys](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html).
7. [Spring Security — Multi-Factor Authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/mfa.html).
8. [Spring Security — OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/).
9. [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
10. [RFC 10017 — OAuth 2.0 for Browser-Based Applications](https://www.rfc-editor.org/rfc/rfc10017).

**Оригинален example repository на лекторите:** не е надеждно идентифициран в транскрипта; не заместваме това с измислен линк.
