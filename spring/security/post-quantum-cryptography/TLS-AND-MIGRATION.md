# Post-Quantum TLS 1.3 и план за миграция — Java / Spring Gateway

> **Архитектурна и operational бележка, не runnable TLS integration test.** Java 25 лабораторията демонстрира ML-KEM/ML-DSA/KDF локално. Тази глава се отнася до **реален** TLS 1.3 handshake с Java 27 SunJSSE (или конкретна vendor LTS backport версия, доказана с release notes и тест).

## 1. Production scenario

```text
Browser ─HTTPS─> CDN/LB (TLS termination) ─HTTPS/mTLS─> Spring Cloud Gateway ─HTTPS─> Java service
                      A                         B                          C
```

Обновяваш Java runtime на service C и виждаш `ML-KEM` в списъка на JCA providers. Това **не доказва**, че трафикът от browser до CDN е с post-quantum hybrid key exchange. На всеки hop (участък) peer-овете договарят свой TLS режим; част от трафика може дори да е без TLS, ако е така конфигурирана средата.

**Bad:** „JDK поддържа ML-KEM → вече сме защитени.“ **Good:** криптографски inventory по hop + наблюдавана negotiated group + сертификатен и signing inventory.

## 2. Версии и алгоритми

| Тема | Къде е налична | Какво доказва / не доказва |
| --- | --- | --- |
| `KeyPairGenerator("ML-KEM")`, `KEM("ML-KEM")`, `Signature("ML-DSA")` | JDK 24+ със съответния provider | Локален primitive; **не** TLS key exchange |
| `KDF("HKDF-SHA256")` | JDK 25 stable API (JEP 510) | Деривация на ключ; **не** protocol security |
| `X25519MLKEM768` (default), `SecP256r1MLKEM768`, `SecP384r1MLKEM1024` | JDK 27 SunJSSE (JEP 527); част от възможностите може да са backport-нати | PQ/T hybrid *key establishment* **само когато е договорен с peer-а** |
| TLS сертификатни подписи (RSA/ECDSA) | Зависи от PKI/CA/client | **Остават отделна повърхност за миграция** |
| JWT/JOSE/JWS и OIDC подписи | Зависи от identity provider, verifier и JOSE ecosystem | **Не** се мигрират от JVM upgrade |

RFC 10024 дефинира трите TLS hybrid groups. `X25519MLKEM768` комбинира X25519 ECDHE с ML-KEM-768. Идеята на hybrid construction е да има защита, докато поне една съставка остава сигурна **при гаранциите на стандартизирания протокол**; не преписвай сам combiner (правило за съчетаване на две тайни) към произволен application protocol.

## 3. Как наистина проверяваме TLS

### Проверка A — кой терминира TLS?

Изброй реалните peer-ове: browser↔CDN/LB, LB↔Gateway, Gateway↔service и всякакъв ingress/service mesh. За всеки hop запиши:

- client/server software, версия, Java distribution или non-Java TLS implementation;
- TLS 1.3 активен ли е, кои `named groups` (групи за обмен на ключове) са поддържани;
- real negotiated group, **не само offered/supported groups**;
- сертификати, chain, CA, алгоритми на подписите и mTLS настройки;
- rollback plan (план за връщане назад) и observability за handshake errors.

### Проверка B — Java 27 SunJSSE handshake

В **тестова среда с нечувствителни сертификати/трафик** активирай ограничена диагностика:

```bash
java -Djavax.net.debug=ssl:handshake -jar your-app.jar
```

Осъществи реална TLS 1.3 връзка с peer, който поддържа `X25519MLKEM768`. Провери `ServerHello.key_share.server_share.named group`. При успешна hybrid negotiation очакваният indicator според JDK 27 release notes е:

```text
"key_share (51)": {
  "server_share": {
    "named group": x25519mlkem768
  }
}
```

**Това е формат от официалната документация, не лог от изпълнена в тази лаборатория връзка.** Debug изходът може да съдържа metadata за сертификати, хостове и handshakes; не го включвай безконтролно в production и не го публикувай.

### Проверка C — fallback и отрицателен сценарий

| Client | Server | Очакване | Защо |
| --- | --- | --- | --- |
| JDK 27 SunJSSE default, TLS 1.3 | Hybrid-capable peer | Възможна `X25519MLKEM768` negotiation | Двете страни я предлагат и избират |
| JDK 27 SunJSSE default, TLS 1.3 | Classical-only peer | **Може да е успешен** handshake с classical `x25519` | Default предлага classical fallback; **успех ≠ PQ защита** |
| Hybrid-only allowlist client | Peer без съвпадаща група | Handshake failure / incompatibility | Няма общо поддържана група; проверявай в canary |

При нужда Java 27 може да настрои `jdk.tls.namedGroups` или `SSLParameters.setNamedGroups()`. **Не** форсирай group-only в production без тестове и rollback. Проверявай custom JSSE provider, FIPS modes, reverse proxy, HTTP client, service mesh и vendor backports. Това не може да се докаже с нашия JUnit KEM тест.

## 4. JWT, PKI и Spring Boot/Gateway

- `JWT` подписан с RS256/ES256 **остава класически подпис** и когато токенът пътува по hybrid TLS. За quantum-resistant signing са нужни съвместими JOSE спецификации, Identity Provider, verifiers, алгоритми, сертификати/keys и rollout — не заменяй `alg` само в header.
- `mTLS` автентикацията зависи от сертификати и подписи; PQ key exchange и PQ authentication са две независими оси. Hybrid TLS по JEP 527 се отнася до key exchange, **не** автоматична PQ сертификатна верига.
- `Spring Cloud Gateway` не може да наложи cryptographic mode на frontend CDN терминиращ TLS преди него. Проверявай всеки hop и собствените `SslContext`/provider версии; test с реални peer endpoints.
- `Redis`, `Oracle`, KMS/HSM, message broker и други дълго живеещи зависимости имат свой TLS stack. Разширяването на обхвата е threat-driven, не „един флаг за всички“.
- Password databases: продължавай с `Argon2id`/`bcrypt` и съответните auth controls. Постквантова миграция не е масова смяна на password hashing.

## 5. Конкретен rollout plan и decision gates

1. **Data longevity (живот на чувствителността):** приоритизирай комуникации/архиви с години изисквана поверителност, особено ако могат да бъдат записвани сега.
2. **Crypto inventory:** документирай TLS termination, JDK/vendor/provider, TLS 1.3 groups, сертификатни подписи, token signing, KMS/HSM, DB encryption и retention.
3. **Версиите:** гледай release notes на реално инсталирания runtime, не приемай, че JDK 25 винаги има всички backports или че Java 27 гарантира избор на hybrid group.
4. **Canary peer matrix:** 27→27, 27→classical, reverse proxy→Gateway, cross-language clients; записвай negotiation и handshake/error rate. Отделни тестове за TLS сигурност, throughput и p95 latency.
5. **Observability & rollback:** наблюдавай fallback към classical и грешки. Връщане назад без прекъсване на критичен трафик. Логвай само metadata без secrets.
6. **Signing ecosystem:** собствен план за JWT, certificate PKI и подписани архиви. Не обявявай „fully quantum-safe“, докато подписите не са оценени.
7. **Ключово управление:** rotation, revocation, backups, provider supply-chain, key generation entropy, KMS compatibility и performance.

### Decision contract

| Решение | Проблем | Използвай когато | Не използвай когато | Печалба | Загуба / алтернатива | Доказателство |
| --- | --- | --- | --- | --- | --- | --- |
| Default hybrid group | HNDL на TLS key exchange | И двата peer-а поддържат, рискът оправдава | Не знаеш TLS terminator или peer групите | HNDL resilience | По-голям handshake, compatibility; managed provider с план | Реален `ServerHello` log |
| Forced hybrid-only | Не допускаме classical fallback | Има policy, всички peer-ове са проверени и може rollback | Клиенти с неизвестни TLS stacks | Строга политика | Availability/interop риск; staged rollout | Negative handshake test |
| Migration на ML-DSA подписи | Дългосрочна цялост/произход | Целият signing/verification ecosystem поддържа | Само client/server JDK upgrade | Quantum-resistant primitive | Signatures/keys и PKI/JOSE support; поетапна миграция | JUnit за primitive + external interop tests |

**Проверим финал:** „Знам кои connection hops реално преговарят `X25519MLKEM768` и кои все още използват classical exchange“ е валидно твърдение. „Обнових JDK, значи всичко е PQ“ не е.

## Оригинални източници

- [Julian Burr — Post Quantum Today, Devoxx](https://www.youtube.com/watch?v=yUMnuh5U5k4) (лекцията е обзор, не Java/TLS implementation guide).
- [JEP 527 — Hybrid TLS 1.3](https://openjdk.org/jeps/527).
- [RFC 10024 — PQ/T Hybrid TLS 1.3](https://www.rfc-editor.org/rfc/rfc10024.html).
- [Oracle JDK 27 Security Enhancements](https://inside.java/2026/09/16/jdk27-security-enhancements/).
- [Oracle JDK 27 Release Notes — debug negotiation](https://www.oracle.com/java/technologies/javase/27-relnote-issues.html).
- [NIST FIPS 203](https://csrc.nist.gov/pubs/fips/203/final), [FIPS 204](https://csrc.nist.gov/pubs/fips/204/final).
- [Java Evolution Guide `6](../../../java/JAVA-EVOLUTION-GUIDE.md) · [Main lab](README.md).
