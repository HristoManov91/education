# NotebookLM — Post-Quantum Cryptography за Java Backend

## Източници, които да кача

1. [Основният урок](README.md) — първи приоритет за последователността на разказа.
2. [TLS и миграция](TLS-AND-MIGRATION.md) — operational план.
3. [Упражнения](EXERCISES.md) и работещите класове/тестове.
4. [Julian Burr — Post Quantum Today, Devoxx](https://www.youtube.com/watch?v=yUMnuh5U5k4) + предоставения SRT (ако е наличен в NotebookLM).
5. [NIST FIPS 203](https://csrc.nist.gov/pubs/fips/203/final), [FIPS 204](https://csrc.nist.gov/pubs/fips/204/final), [FIPS 205](https://csrc.nist.gov/pubs/fips/205/final), [JEP 496](https://openjdk.org/jeps/496), [JEP 497](https://openjdk.org/jeps/497), [JEP 510](https://openjdk.org/jeps/510), [JEP 527](https://openjdk.org/jeps/527), [RFC 10024](https://www.rfc-editor.org/rfc/rfc10024.html).

## Готов prompt за аудио урок (на български)

```text
Създай разговорен, задълбочен, но практически фокусиран аудио урок на БЪЛГАРСКИ за Java 25 / Spring Boot backend инженер.

Започни от реален казус: Browser → TLS terminator/CDN → Spring Gateway → Java service → дългосрочен конфиденциален архив. Нападателят записва трафик сега, за да го декриптира в бъдеще. Защо това е риск, преди да имаме практически квантова атака?

Стъпка по стъпка обясни:
1. Каква е разликата между password hashing (Argon2id/bcrypt), AES-GCM, RSA/ECDH/ECDSA, key encapsulation (ML-KEM), digital signature (ML-DSA).
2. Какъв е концептуалният проблем с RSA/ECC и Shor. Квантовата физика и lattice learning-with-errors да останат кратки и интуитивни, без недоказани количествени прогнози.
3. HNDL: какво може да бъде събрано сега, колко дълго остава чувствително, и защо рискът не е еднакъв за всеки продукт.
4. NIST FIPS 203/204/205 и Java 24/25/27 version boundaries. Ясно различавай ML-KEM primitive от TLS hybrid X25519MLKEM768.
5. Разкажи кода QuantumSafeEnvelope.java: ML-KEM recipient key pair → encapsulation → HKDF-SHA256 със salt и domain separation → AES-256-GCM със nonce и tenant AAD → decapsulation/decrypt. Изрично кажи, че това е учебна схема, НЕ стандартизиран secure messaging protocol.
6. Разкажи positive/negative JUnit tests; особено защо ML-KEM implicit rejection при same-length tampering може да доведе до различен secret без exception, а AES-GCM трябва да откаже authentication.
7. Разкажи PostQuantumSignatures.java: ML-DSA sign/verify, modified message, wrong public key; какво НЕ доказва за JOSE/JWT, CA, PKI, mTLS.
8. Разкажи реалния TLS 1.3 migration план: ingress/CDN, Gateway, Java vendor release, negotiated hybrid group в ServerHello, classical fallback, canary и rollback.
9. Дай decision contract за ML-KEM+AEAD, ML-DSA и hybrid TLS: проблем, кога да/не, ползи, trade-offs и по-проста алтернатива.
10. Завърши с 5 въпроса за активно припомняне и конкретни действия по crypto inventory.

Източници: използвай SRT за framing и мотивите на Julian Burr, но официалните NIST/JEP/RFC/Oracle документи за нормативни имена, API, версии и протоколни гаранции. Отбелязвай кое идва от лектора и кое е наша техническа надстройка. Не твърди, че срокове на cloud providers и прогнози за qubits са сигурни. Не казвай, че смяната на паролни хешове е необходима, или че Java 27 прави JWT/сертификатите quantum-safe. Не представяй manual TLS notes като автоматизирани тестове.

Цел: след слушане инженерът да може да обясни и тества primitive-ите и да планира реална, измерима миграция без счупване на production.
```

## След аудиото

Реши [въпросите и задачите](EXERCISES.md) без да гледаш отговорите. За практическите тестове изпълни Java 25 Maven module. Не отбелязвай темата като усвоена само защото е слушана.
