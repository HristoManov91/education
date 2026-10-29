# Bootiful Security — практически задачи и проверка на знанията

> Упражненията **не са оценки за усвоено знание**. Предназначени са за 20–30-минутни сесии и независима проверка; не добавяме отметки за успешно решаване преди реални отговори. Започни от [README](README.md), след това премини към [OAuth2/BFF/MCP](OAUTH2-BFF-MCP.md).

## Лаборатория A — HTTP security mental model (20 мин)

**Казус:** нов developer добавя `GET /api/admin/export`. Той мисли, че след като endpoint-ът съществува, admin получава 200 автоматично.

Задачи:

1. Преди да стартираш теста, прогнозирай статусите за anonymous, USER, ADMIN без OTT и ADMIN с двете факторни authorities.
2. Добави нов endpoint `GET /api/invoices/{id}`, който да е достъпен само за собственик или finance role. Първо напиши failing HTTP test.
3. Провери дали `anyRequest().denyAll()` предотвратява случайно отваряне на endpoint при грешен matcher.
4. Добави security regression test, който не използва `@WithMockUser` за **реално** login доказателство, а извиква `POST /login` с `csrf()` и тестови credentials.

**Proof:** правилни 200, redirect/401, 403; приложението не позволява непознати routes. **Root cause:** ролята, факторът и идентичността не се проверяват на едно и също ниво.

## Лаборатория B — password storage и rehash (25 мин)

Преди изпълнение отговори: защо SHA-256 е добро за integrity на файл, но лошо за password hash? Как работи salt и защо два hash-а на една парола трябва да са различни?

1. Пусни `PasswordHashTest`, предвиди резултатите.
2. Симулирай legacy bcrypt cost=4, съвременен encoder с по-висока цена и провери `matches` плюс `upgradeEncoding`.
3. Реализирай в **отделен exercise branch** `UserDetailsPasswordService`, който презаписва hash при успешен login. Добави test, който доказва, че при грешна парола нищо не се обновява.
4. Напиши миграционен план за Oracle/PostgreSQL users таблица: nullable/safe schema evolution, rolling deployments, hash id, account lockout, compare-and-swap/concurrency и rollback.

**Proof:** добър hash се използва след login; не сме променяли password за потребител, който не е логнал; race-condition не губи нова парола.

## Лаборатория C — OTT, passkeys, MFA и CSRF (25 мин)

1. Предвиди какво става ако една и съща one-time стойност се изпрати два пъти. Докажи със `OneTimeTokenServiceTest`.
2. Защо е опасно да доставяме magic link като response от `POST /ott/generate`? Какво се променя при email preview/scanner?
3. Защо `FACTOR_OTT` НЕ трябва да се съхранява като перманентна business role в users таблицата?
4. Какво е различното между WebAuthn и email OTT? Къде физически остава private key?
5. Отговори дали CSRF защита е нужна на `POST /api/admin/operation`, когато клиентът е браузър с cookie-based session и успешен MFA.

**Proof:** replay е rejected, ADMIN+MFA не заобикаля CSRF, user без role не получава admin достъп.

## Лаборатория D — OAuth2/OIDC triage (25 мин)

**Казус:** потребител е логнат в BFF, но downstream service връща `401 invalid_token`.

Провери последователно:

1. Дали BFF изпраща **access token**, а не ID token?
2. Дали token `iss` е trusted, подписът съответства на активен JWKS key, `exp` и `nbf` са валидни?
3. Дали API проверява очаквания `aud` и дали client registration има правилните scopes?
4. Дали token replay/cross-service forwarding може да наруши trust boundary?
5. Как различаваш липса на authorization от липса на authentication (401 vs 403)?

**Proof:** конструирай отрицателни тестове за wrong issuer, wrong audience, expired token и missing scope. Не се задоволявай с един тест „JWT декодира успешно“.

## Лаборатория E — BFF и MCP архитектурен review (25 мин)

**Казус:** разработчик предлага:
```text
React SPA stores access token in localStorage
    -> calls /api via CORS
    -> gateway blindly forwards token to all services
    -> AI agent reuses a global ADMIN bearer for booking appointments
```

Предложи безопасна алтернатива:

- Browser → same-origin BFF с HttpOnly/Secure session; server-side OAuth tokens.
- TokenRelay единствено към предвидения downstream API; static UI route без токен.
- OAuth issuer/audience/scope validation във всеки resource server.
- MCP agent като **отделен OAuth client** с delegated user access, per-tool authorization, rate limits и audit.
- За мутиращи tools: business ownership, explicit confirmation при риск, idempotency и prompt-injection защити.

**Proof:** обясни кой държи credentials във всеки hop, кой решава access и на коя граница token може/не може да се препредава.

## Финален Senior challenge — 10 въпроса за активен recall

1. Каква е разликата между authentication, authorization, OAuth2 scope, role и factor authority?
2. Защо `hasRole("ADMIN")` не доказва, че потребителят току-що е потвърдил своята самоличност?
3. Защо Argon2id има предимство пред бърз SHA-256 за пароли?
4. Как работи upgrading на password hashes **без** database migration със знание за plaintext?
5. Защо email OTT може да е по-податлив на phishing от passkey?
6. Какво съхранява WebAuthn сървърът и какво остава на устройството?
7. Защо един валиден OAuth access token може да е **невалиден за конкретен API**?
8. Какво прави BFF със server-side tokens и защо това не отменя CSRF?
9. Какво точно прави TokenRelay и защо не се използва за UI assets?
10. Ако AI tool връща „booked successfully“, как доказваш, че операцията е извършена от правилния потребител и само веднъж?

**Engineering изход:** можеш ли да нарисуваш trust boundaries, да изброиш отрицателните тестове и да посочиш кои фрагменти от лекцията демонстрират реален security invariant — вместо просто да помниш API имена?
