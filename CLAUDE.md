# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**DocuRo** — a Romanian-focused intelligent document wallet. Users upload personal documents (buletin, RCA policy, vehicle registration, etc.) and the app extracts structured fields using vision LLMs.

Alwyas use @SPEC.md for the full architecture, data model, security model, API design, and milestones.

## Current State

Milestone 1 backend is scaffolded. See SPEC.md for what remains to be implemented.

- `src/` — Micronaut/Kotlin backend (auth, document upload, Gemini extraction, encryption)
- `SPEC.md` — architecture, data model, security model, API design, milestones
- `docs/VISION.md` — full product vision and JSON schemas per document type
- `scripts/` — Python proof-of-concept scripts for Gemini API validation
- `docker-compose.yml` — PostgreSQL 16 + MinIO

## Running the validation scripts

```bash
cd scripts
python -m venv venv && source venv/bin/activate
pip install google-genai python-docx
GEMINI_API_KEY=<your_key> python test_model_image.py
GEMINI_API_KEY=<your_key> python test_model_docx.py
```

## Running the backend locally

**Prerequisites:** Docker, JDK 21

**`docker-compose.yml`** (already in repo root):

```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: docuro
      POSTGRES_USER: docuro
      POSTGRES_PASSWORD: docuro
    ports:
      - "5432:5432"

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"   # S3 API
      - "9001:9001"   # MinIO console
```

```bash
# 1. Start PostgreSQL + MinIO
docker compose up -d

# 2. Run the server — Flyway migrations run automatically on startup
GEMINI_API_KEY=<your_key> ./gradlew run

# Server:        http://localhost:8080
# MinIO console: http://localhost:9001  (minioadmin / minioadmin)
```

| Variable | Default (dev) | Notes |
|---|---|---|
| `GEMINI_API_KEY` | — | Required |
| `JWT_SIGNING_SECRET` | `changeMeInProductionSigningSecret32c` | Min 32 chars |
| `JWT_ENCRYPTION_SECRET` | `changeMeInProductionEncryptionSec` | Min 32 chars |
| `DB_URL` | `jdbc:postgresql://localhost:5432/docuro` | |
| `DB_USER` / `DB_PASSWORD` | `docuro` / `docuro` | |
| `MINIO_ENDPOINT` | `http://localhost:9000` | |
| `STORAGE_BUCKET` | `docuro-documents` | |

## Example: register, login, upload

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "test@docuro.ro", "password": "MySecurePass123!"}' | jq

# Login — capture JWT (JWE-encrypted, contains session DEK)
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "test@docuro.ro", "password": "MySecurePass123!"}' \
  | jq -r '.access_token')

# Upload a document (image, PDF, DOCX, or plain text)
curl -s -X POST http://localhost:8080/api/documents/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/buletin.jpg" | jq

# Fetch extracted fields (sensitive fields decrypted automatically for this user)
curl -s http://localhost:8080/api/documents/<documentId> \
  -H "Authorization: Bearer $TOKEN" | jq

# List all documents for the authenticated user
curl -s http://localhost:8080/api/documents \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Testing guidelines

- **Unit tests** — use MockK for mocking; use a real `EncryptionService` instance (no external deps) rather than mocking crypto.
- **Integration tests requiring PostgreSQL or S3/MinIO** — always use Testcontainers. Never assume Docker Compose is running. The pattern is:
  1. Add `org.testcontainers:postgresql` and/or a `GenericContainer("minio/minio")` in the test companion object, started with `.also { it.start() }` so they are up before Micronaut's JUnit extension runs.
  2. Implement `TestPropertyProvider` to supply the dynamic `datasources.default.url`, `aws.services.s3.endpoint-override`, etc., derived from the running containers.
  3. Annotate the test class with `@MicronautTest` and `@TestInstance(TestInstance.Lifecycle.PER_CLASS)`.

  ```kotlin
  @MicronautTest
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  class MyIntegrationTest : TestPropertyProvider {
      companion object {
          val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
              .withDatabaseName("docuro").withUsername("docuro").withPassword("docuro")
              .also { it.start() }
          val minio: GenericContainer<*> = GenericContainer("minio/minio:latest")
              .withExposedPorts(9000)
              .withEnv("MINIO_ROOT_USER", "minioadmin").withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
              .withCommand("server /data").also { it.start() }
      }
      override fun getProperties() = mapOf(
          // Build the JDBC URL manually — postgres.jdbcUrl can return a partial value if the
          // env var DB_URL is set to an empty string and the property is read before the
          // container is fully up. getMappedPort(5432) is always reliable after start().
          "datasources.default.url"           to
              "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
          "datasources.default.username"      to postgres.username,
          "datasources.default.password"      to postgres.password,
          "aws.services.s3.endpoint-override" to "http://${minio.host}:${minio.getMappedPort(9000)}",
          "docuro.gemini.api-key"             to "test-placeholder",
      )
  }
  ```
- Do **not** use `@Testcontainers` / `@Container` JUnit annotations when combined with `@MicronautTest` — the extension ordering is not guaranteed; start containers in the companion object init block instead.

## Code guidelines

- **GraalVM compatibility** — avoid reflection-heavy libraries without GraalVM config; Micronaut uses compile-time DI specifically for this reason.
- **No hardcoded secrets** — all API keys and credentials must come from environment variables.
- **Auth on every document endpoint** — all `/api/documents/**` routes must be `@Secured(SecurityRule.IS_AUTHENTICATED)`.
- **GDPR** — data must remain in `eu-central-1`. Never log CNP or financial field values in plaintext.
- **First page only (Phase 1)** — PDF extraction uses page 0 only; multi-page support is deferred.
- **Prompt files** — edit `src/main/resources/prompts/*.txt` to tune extraction quality without recompiling.
- **Environment variables** - we use direnv with .env files to configure environment-specific variables, including sensitive data
