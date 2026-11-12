# Real OpenFHE BFV — separate client/worker native integration

> **Истински OpenFHE C++ чрез официалните Python bindings, не криптографска имитация.** Чете [общите Java/Python CSV сценарии](../src/test/resources/risk-cases.csv). За теория и trade-offs виж [основния урок](../README.md).

## 1. Съвместима среда

Официалният PyPI build [`openfhe==1.6.1.0.24.4`](https://pypi.org/project/openfhe/1.6.1.0.24.4/) е публикуван на 27.10.2026 за **Ubuntu 24.04 + Python 3.12**. Не приемай, че `py3-none-any` означава ABI portability за тази native библиотека. За Ubuntu 22.04 има различен build suffix `.22.4` и Python ABI; за Mac/Windows следвай [OpenFHE инструкции](https://github.com/openfheorg/openfhe-python), вместо да насилваш Ubuntu binary. Wheel-ът няма PyPI provenance attestation. Той не се инсталира при default Maven `verify`.

```bash
# from education root:
cd spring/security/privacy-preserving-computation/openfhe-lab
python3.12 -m venv .venv
source .venv/bin/activate
python -m pip install --no-cache-dir -r requirements-ubuntu24.txt
python -m unittest -v test_protocol       # 8 offline tests, no external package needed
python -m unittest -v test_real_openfhe   # MUST use real OpenFHE or FAIL
python client.py                          # separate client/worker subprocess
python benchmark.py --runs 3              # optional, not in CI
```

CI е [path-limited workflow](../../../../.github/workflows/privacy-fhe-lab.yml): Ubuntu 24.04, Python 3.12, само при FHE промяна в PR или ръчно чрез `workflow_dispatch`. Няма случайни скъпи зависимости в основния Maven reactor.

## 2. Данни, ключове и граници

[client.py](client.py) създава `CCParamsBFVRNS`, `SetPlaintextModulus(65537)`, `SetMultiplicativeDepth(1)`, генерира ключове и криптира шест **SIMD packed vectors по 8 фикстури**. Частният ключ остава в паметта на клиента. Временната директория (от `TemporaryDirectory`) съдържа само:

```text
manifest.json                   (profile/version, batch size, SHA-256 fingerprints)
context.bin                     (public crypto context, NO private/secret key)
age.bin, total_cholesterol.bin, hdl_cholesterol.bin,
systolic_pressure.bin, hypertension.bin, smoker.bin    (ciphertexts)
response.bin                    (ciphertext score vector from worker)
metrics.json                    (timings/byte counts, no plaintext input)
```

[server.py](server.py) се стартира като **отделен локален Python процес**, валидира документа и десериализира контекста и ciphertext полетата. `EvalMult(ciphertext, plaintext_weight)` и `EvalAdd` изчисляват сбор от шест члена; работникът **не получава** private key. Клиентът десериализира `response.bin` и декриптира всички 8 резултата, тогава класифицира LOW/MEDIUM/HIGH.

**Този file transport НЕ е production HTTP microservice:** няма TLS/authenticated channel, endpoint/tenant authZ, malicious-worker response proof, CPU isolation, native library sandbox или remote attestation. `TemporaryDirectory` не означава crypto secure erase. И двата процеса са на един host. За реален distributed design са нужни независими trust boundaries.

## 3. Negative tests: не обещаваме повече от доказаното

| Източник | Проверяваме | Не доказваме |
| --- | --- | --- |
| [test_protocol.py](test_protocol.py) | Missing/extra fields, tampered bytes, unsupported claimed modulus, unvalidated input, manifest size | Cryptographic authentication на payload, honest input |
| [test_real_openfhe.py](test_real_openfhe.py) | Реална BFV декрипция върху 8 cases (signed -400, 499/500, 999/1000) | Приложимост за произволен circuit, noise exhaustion и security certification |
| [RiskScoreCalculatorTest.java](../src/test/java/bg/hristomanov/education/privacy/RiskScoreCalculatorTest.java) | Независим Java mathematical oracle, input bounds и centered residue | Действителната FHE библиотека без Python тест |

`manifest.json` съдържа SHA-256 на контекста и ciphertext. Това открива случайно повреден файл, **но не е защита срещу злонамерен противник**, който може да преизчисли хешовете. Проверяването на заявеното `plaintext_modulus` не доказва, че реалният C++ context има secure и допустими параметри. Production вариант трябва да има проверен context profile, authenticated transport, контроли върху ресурсите и според модела — proof, че encrypted inputs са в зададения диапазон.

Не сме добавили нестабилен „очаквай silent noise overflow“ тест без фиксиран контекст/дълбочина и възпроизводими условия. Това е отделен експеримент, не заместител на първоначалните **истински** FHE oracle tests.

## 4. Performance

`client.py` отчита context/keygen, encrypt, serialization, subprocess roundtrip, server EvalMult/EvalAdd, server SerializeToFile, decrypt и bytes. [benchmark.py](benchmark.py) прави няколко пълни повторения и показва медиани. **Не сравнявай чист Python in-process scoring с OpenFHE subprocess end-to-end и не наричай това един и същ benchmark.** За production sizing измервай на един hardware/CPU, с pinned versions, warmup, реални размери на batch, memory/RSS, различни parameter sets и uncertainty intervals.

Лекцията споменава 0.43 μs срещу 7.71 ms за нейния пример (~18 000×). Нашите реални числа трябва да идват от **нашите изпълнения**, а не от презентацията.

## 5. Decision contract

**Проблем:** worker не бива да вижда private входовете.

**Кога:** малък integer weighted-score circuit при силна confidentiality нужда.

**Кога НЕ:** динамична SQL/CRUD логика, тежко branching, стриктен real-time latency SLA.

**Плюс:** изчисление без decryption key. **Минуси:** CPU/RAM/payloads, noise/parameter risks, native Python ABI, process startup.

**По-просто:** доверена backend услуга с TLS/TDE и KMS/access policies или client-side aggregation; при други threat models — TEE/MPC.

**Proof:** [Java tests](../src/test/java/bg/hristomanov/education/privacy/RiskScoreCalculatorTest.java) + [8 protocol tests](test_protocol.py) + [real BFV](test_real_openfhe.py).

Официални източници: [OpenFHE Python wrapper](https://github.com/openfheorg/openfhe-python), [serialization example](https://github.com/openfheorg/openfhe-python/blob/main/examples/pke/simple-real-numbers-serial.py), [PyPI](https://pypi.org/project/openfhe/1.6.1.0.24.4/).
