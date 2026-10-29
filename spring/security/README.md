# Spring Security — learning path

## P1: Bootiful Security (Devoxx 2026)

- [Bootiful Security — practical Spring Security 7 lab](bootiful-security/README.md): AuthN/AuthZ, bcrypt/DelegatingPasswordEncoder, local-only OTT, step-up MFA, secure defaults and HTTP tests.
- [OAuth2/OIDC → BFF/TokenRelay → MCP](bootiful-security/OAUTH2-BFF-MCP.md): доверителни граници и архитектурни модели (документирани, не fake runnable services).
- [Exercises и active recall](bootiful-security/EXERCISES.md): проблеми, отрицателни тестове и Senior review въпроси.

**Принцип:** authentication identity, authorization decision и factor strength са отделни concerns. В production предпочитай управляван Identity Provider, когато нямаш добра причина да поддържаш identity инфраструктура сам.

Свързани теми: [Spring Testing](../testing/ai-assisted-testing/README.md), [Spring Reliability](../reliability/idempotency/README.md), [AI Agentic Development](../../ai/agentic-development/README.md).
