# Продължение: OAuth2/OIDC → Resource Server → BFF → защитен MCP

> Секция по втората половина на **Bootiful Security** (мин. ~27–50). **Архитектурен материал с конфигурационни примери**, не готов multi-process demo. За работещия single-app lab виж [README](README.md).

## 1. Реален казус

Имаме enterprise система от 20 приложения. Не искаме 20 users таблици, 20 пароли и 20 различни механизма за logout/revocation. Имаме browser SPA, която прави заявки към billing/report API. Появява се и AI агент, който може да заявява и променя appointments от името на конкретен потребител.

**Naive:** `frontend → получава access token → пази в localStorage → добавя Authorization bearer към всяка заявка`. Всяка backend услуга приема „валидно изглеждащ JWT“. Agent-ът използва общ system token за всички потребители.

**Root cause:** бъркаме login, token possession и business authority; смесваме trust boundaries и губим user-delegated permissions.

**Good:** един доверен IdP, правилно разделени OAuth роли, BFF за browser app, resource server проверки и tools със собствена authorization политика.

## 2. Трите роли на OAuth2 и четвъртият участник

```text
             browser-based user
                    |
                    v
    +-------------------------------+
    | OAuth Client / BFF            |
    | session + server-side tokens  |
    +-------------------------------+
      |        ^               |
login |        | code          | Authorization: Bearer ACCESS TOKEN
      v        |               v
    +-----------------+   +---------------------+
    | Authorization   |   | Resource Server     |
    | Server / IdP    |   | verifies JWT, scope |
    | OIDC issuer     |   | and business policy |
    +-----------------+   +---------------------+
```

- **Authorization Server**: автентикира потребителя, регистрира OAuth clients, управлява grant/consent и издава tokens. Примери: Spring Authorization Server, Keycloak, Okta, Auth0.
- **OAuth Client**: пренасочва browser към IdP, обработва authorization code, поддържа authorized client и получава tokens.
- **Resource Server**: приема *access token*, проверява issuer, подписи, validity, правилния audience, scopes и приложни права.
- **Resource Owner**: потребителят, от чието име определена клиентска програма получава делегиран достъп (според конкретния flow).

### Не смесвай ID token и access token

| Обект | За кого е? | За какво е? | Опасна грешка |
| --- | --- | --- | --- |
| OIDC **ID token** | OAuth/OIDC client | Доказва резултата от authentication към client | API приема ID token вместо access token |
| OAuth **access token** | конкретен resource server | Разрешен достъп по scopes/audience/policies | Валиден token за Service A се приема от Service B |
| **Refresh token** | OAuth client | Получаване на нов access token | Пази се в browser JavaScript |
| **Session cookie** | browser → BFF | Browser session, не API delegation | Смятаме, че login cookie отменя CSRF |

**Важно:** подписан JWT сам по себе си не означава, че caller има право да изпълни всяка операция. Сървърът трябва да валидира `iss`, `exp`/`nbf`, `aud` (когато API очаква конкретен audience) и бизнес ограниченията.

## 3. Login flow: Authorization Code + OIDC

```text
1. Browser -> BFF: GET /account
2. BFF -> IdP: redirect to /authorize (state, nonce as applicable, PKCE)
3. User -> IdP: authentication + consent
4. IdP -> BFF callback: short-lived authorization code
5. BFF (backend) -> IdP token endpoint: code exchange
6. BFF stores tokens on server; browser receives session cookie
7. Browser -> BFF: GET /api/messages
8. BFF -> API: Authorization: Bearer access_token
9. API verifies token + audience + scope + domain policy
```

**Проверки:** secure redirect URI, `state` срещу CSRF на OAuth callback, PKCE, коректни allowed origins, discovery/JWKS, clock skew, logout и session invalidation, token rotation и secret storage. Възможно е IdP да е външна управлявана услуга, без да пишем свой authorization server.

### Минимален OAuth Client — илюстративен Boot 4 config

В отделно BFF приложение добавяме `spring-boot-starter-oauth2-client` (НЕ към текущия single-app lab) и configure-ваме:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          education:
            provider: corporate-idp
            client-id: education-bff
            client-secret: ${OAUTH_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            scope: openid,profile,messages.read
        provider:
          corporate-idp:
            issuer-uri: https://idp.example.test/realms/education
```

`openid` указва OIDC login; client secret идва от deployment secrets, не от Git. Това не е plug-and-play config: IdP трябва да има регистриран client, валиден redirect URI и разрешени scopes.

## 4. Resource Server: verify, не просто decode

В отделно API приложение добавяме `spring-boot-starter-oauth2-resource-server`:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://idp.example.test/realms/education
```

Resource Server може да открие JWKS/public keys от issuer metadata и да проверява токена. За API, което изисква specific audience (например `education-api`), добави изрична audience validation; `issuer-uri` само по себе си не е заместител на този контракт. Типично authorities на resource server включват `SCOPE_messages.read`.

**Bad:** `Base64.decode(payload)`, извличаме `sub` и допускаме request. Това не проверява signature, issuer, audience или срок.  
**Good:** използвай Spring `oauth2ResourceServer().jwt(...)` и добави необходимите validators + authorization rules. Неправилни, изтекли или невалидни bearer tokens -> HTTP 401; валиден caller без права -> HTTP 403.

### Проверки на API-то

- Без token -> 401 (за stateless resource server).
- Token за друг issuer -> 401.
- Token с грешен audience -> 401 след правилен custom validator.
- Token с валиден подпис, но missing scope/role -> 403.
- Token от предишна key rotation и валиден срок -> решението зависи от актуалните trusted signing keys; провери rotation policy.
- Правилни token claims, но потребител без права върху конкретния resource -> 403 / policy-specific отговор. Scope не отменя object-level authorization.

## 5. Token Relay и RestClient

Лекторите първо добавят Bearer header ръчно към downstream RestClient. Това изглежда лесно, но caller context и authorized client се размазват из business code. След това го изнасят в interceptor и накрая в декларативен HTTP client.

**Good dependency:** `OAuth2AuthorizedClientManager` управлява access token acquisition/reuse/refresh според grant и client registration. `OAuth2ClientHttpRequestInterceptor` може да добавя Bearer token към конфигуриран RestClient.

```java
// Илюстративен фрагмент за приложение с OAuth Client starter:
OAuth2ClientHttpRequestInterceptor interceptor =
        new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);

RestClient client = RestClient.builder()
        .baseUrl("https://api.example.test")
        .requestInterceptor(interceptor)
        .build();
```

Това **не означава** „винаги вземи някакъв token“. Изборът на client registration, authorized principal и audience трябва да е изричен. Не ползвай потребителски token като machine-to-machine service token без основания и договорен trust model. Не предавай ID token на Resource Server.

Официално: [Authorized Client features](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorized-clients.html).

## 6. BFF за SPA — централното production решение

**Рискът:** браузърът е по-уязвима среда за дългоживеещи credentials. Токени в localStorage, URL или достъпни JS обекти се излагат при XSS и dependency compromise.

**Backend for Frontend (BFF)** е OAuth confidential client зад browser-facing origin. Той пази access/refresh tokens на сървъра и предоставя на SPA само session cookie. SPA заявява `/api/...` на **същия origin**, а BFF маршрутизира към API и прилага `TokenRelay`. Това е вариантът, демонстриран чрез Spring Cloud Gateway MVC.

```text
https://app.example.test/
    /index.html    -> static UI (НЕ forward-ваме access token)
    /api/messages  -> BFF -> messages-api (+ нужния user access token)
```

Илюстративни routes за **Spring Cloud Gateway Server MVC** (версиите и starter зависимостите трябва да бъдат съвместими с конкретния Spring Boot release):

```yaml
spring:
  cloud:
    gateway:
      server:
        webmvc:
          routes:
            - id: messages-api
              uri: http://127.0.0.1:8081
              predicates:
                - Path=/api/**
              filters:
                - StripPrefix=1
                - TokenRelay=
```

Отделната UI route трябва да обслужва static assets от същия origin, **без** TokenRelay. Route order/precedence трябва да е изричен — catch-all UI route може да погълне API route. CORS често се опростява от same-origin модела, но **CSRF не изчезва**. Cookie и session setup: `HttpOnly`, `Secure`, подходящ `SameSite`, CSRF protection, session fixation protection, logout/revocation, cookie domain/path и TLS. Не допускай arbitrary token relay към недоверени URL адреси.

Към октомври 2026 това е описано и в [RFC 10017 `6.1](https://www.rfc-editor.org/rfc/rfc10017#section-6.1). Съществуват и други допустими архитектури за browser apps; BFF е препоръчителен, когато имаме server-side operational capacity и искаме да държим токените извън JavaScript.

## 7. OAuth-protected MCP — AI агентът също е клиент

В последната демонстрация потребителят Alice има appointments app. Spring AI exposes tools като list/book appointments; Claude Code добавя HTTP MCP endpoint, установява, че изисква OAuth, отваря browser и user логва. Agent-ът получава delegated access token и изпраща заявки до MCP server от името на Alice.

```text
Alice --login/consent--> Authorization Server
Claude Code (OAuth client) --Bearer Alice access token--> MCP Server
MCP Server --verified identity + scopes + ownership policy--> appointments
```

**Критичен нюанс:** излагането на `@Tool` метод само по себе си НЕ прави endpoint сигурен. Spring AI transport auto-config обикновено не налага authentication; security трябва да се добави на HTTP/MCP слой. Spring AI Community MCP Security е community-driven компонент и документацията му е обозначена като work in progress — **свери release compatibility**, преди да го включиш към Boot 4/конкретния Spring AI версия.

Когато tool прави `bookAppointment` или `cancelAppointment`, проверките са на няколко слоя:

1. **OAuth** — валиден delegated token за този MCP server; не generic token от друг resource.
2. **Authorization** — конкретни scopes/claims и object ownership: Alice може да променя своите appointments, не тези на Bob.
3. **User intent** — когато AI предлага чувствително действие, прецени необходимостта от confirm/approval, idempotency и audit.
4. **Prompt injection** — tool input/text от външни източници не трябва да се превръща в привилегировано указание.
5. **Operations** — rate limits, replay protection, logs без secrets, recovery и revocation.

За security-critical tools допълнителната confirmation не е еквивалент на authorization; нужни са и двете според риска.

## 8. Най-честите senior-level капани

| Допускане | Защо е грешно | Правилен модел |
| --- | --- | --- |
| „JWT е подписан => може навсякъде“ | Wrong audience/issuer и липсваща domain policy | Validate identity + audience + scopes + ownership |
| „Имам OIDC ID token, ще го пратя на API“ | ID token е към client-а | API получава access token |
| „SPA на същия domain няма CSRF“ | Browser праща session cookie автоматично | CSRF защита, SameSite и origin validation |
| „Gateway решава всички права“ | Downstream service има собствена authorization boundary | Defense in depth |
| „MCP tool е protected, щом app login работи“ | MCP endpoint има отделен OAuth/auth flow | Protect MCP endpoint + per-tool checks |
| „Имам ROLE_ADMIN, MFA няма нужда“ | Role не доказва актуалността на удостоверяването | Step-up factor + role |
| „Refresh token е валиден дълго, значи е безобиден“ | Компрометира се и се върти трудно | Server-side storage, rotation, revocation |

## 9. Какво все още НЕ е реализирано

Текущият Maven lab **не стартира** Authorization Server, Resource Server, Gateway, browser UI или MCP agent. Това е предвиден следващ, отделен multi-module integration lab с реален IdP, проверки на token claims, testcontainers и браузърни E2E тестове; няма да представяме концептуалния материал като изпълнено покритие.

Следващи практически стъпки: [EXERCISES.md](EXERCISES.md).

## Оригинални източници

- [Bootiful Security — Devoxx 2026](https://www.youtube.com/watch?v=W-OKVLMtPAw), мин. ~27–50.
- [Spring Security — OAuth2 Client / Authorization Server / Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/).
- [Spring Security — Authorized Client features](https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorized-clients.html).
- [Spring Cloud Gateway MVC — TokenRelay](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/filters/tokenrelay.html).
- [RFC 10017 — OAuth 2.0 for Browser-Based Applications](https://www.rfc-editor.org/rfc/rfc10017).
- [Spring AI — MCP Server Security Note](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html).
- [Spring AI Community — MCP Security](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-security.html).
- [OWASP — Cross-Site Request Forgery Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html).
