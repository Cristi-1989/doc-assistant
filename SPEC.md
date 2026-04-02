# DocuRo — Technical Spec

> Architecture, data model, security model, API design, and milestones.
> For how to run the app locally, see **CLAUDE.md**.

---

## 1. What We're Building

A backend service (Micronaut + Kotlin) that:
1. Accepts an authenticated document upload (plain text, DOCX, PDF, or image)
2. Converts it to a format the LLM can read
3. Sends it to `gemini-flash-lite-latest` for field extraction
4. Encrypts the original file with the user's DEK and stores it in MinIO
5. Persists encrypted extracted fields to PostgreSQL

No frontend yet — tested via `curl`.

---

## 2. Tech Stack

### Backend

| Layer | Technology | Rationale |
|---|---|---|
| Language | Kotlin | Concise, null-safe, idiomatic on JVM |
| Framework | Micronaut | Compile-time DI — GraalVM-compatible, fast Lambda cold starts |
| Runtime | JVM (dev) → GraalVM native (prod Lambda) | Fast local iteration, near-zero cold starts in prod |
| Build | Gradle (Kotlin DSL) + KSP | KSP replaces KAPT — up to 2× faster annotation processing |

### Data Layer

| Layer | Technology | Rationale |
|---|---|---|
| Database | PostgreSQL 16 | JSONB column handles flexible per-document-type schemas |
| ORM | Micronaut Data JDBC | Compile-time generated queries, no runtime reflection |
| Object storage (local) | MinIO | S3-compatible, runs as Docker container |
| Object storage (prod) | AWS S3 | Same SDK — swap endpoint URL only |
| Migrations | Flyway | SQL-first, runs automatically on startup |

### LLM

| Task | Model |
|---|---|
| Document type classification | `gemini-flash-lite-latest` |
| Field extraction | `gemini-flash-lite-latest` |

All LLM calls go through `DocumentExtractionPort` — swap the adapter via config without touching business logic.

---

## 3. Architecture

### Data flow

```
POST /api/documents/upload  ← JWT (JWE) required
  → validate token, extract DEK
  → convert input: text passthrough | image passthrough | PDF→image (PDFBox) | DOCX→text (POI)
  → call DocumentExtractionPort (GeminiExtractionAdapter)
  → encrypt file with DEK + random nonce → store in MinIO
  → encrypt sensitive fields (CNP, financial) with DEK
  → persist to PostgreSQL (documents table, encrypted fields as JSONB)
  → return { documentId, status }
```

### Key abstractions

**`DocumentExtractionPort`** (`src/main/kotlin/com/docuro/extraction/DocumentExtractionPort.kt`)
Central interface for all LLM calls. `GeminiExtractionAdapter` is the Milestone 1 implementation.

**`EncryptionService`** (`src/main/kotlin/com/docuro/security/EncryptionService.kt`)
All crypto operations: password hashing, Argon2id KEK derivation, DEK wrap/unwrap, AES-256-GCM file and field encryption.

**Document types** are rows in `document_types(code, label, description, prompt_file)`. New types are added by inserting a row — no code change or redeployment required.

**`fields` column:** JSONB — shape varies by document type. Sensitive values are individually encrypted before storage (see Security Model).

### Extraction prompts

`src/main/resources/prompts/` — one `.txt` file per document type, loaded at runtime (no recompile needed to tune prompts).

```
prompts/
  classify.txt              ← template; {{DOCUMENT_TYPES}} replaced at runtime from document_types table
  buletin.txt
  permis_conducere.txt
  polita_rca.txt
  certificat_inmatriculare.txt
  generic.txt               ← fallback; used when no dedicated prompt_file is configured
```

---

## 4. Data Model

### Users

```sql
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255)        NOT NULL,  -- BCrypt
    kek_salt      BYTEA               NOT NULL,  -- Argon2id salt for KEK derivation
    encrypted_dek BYTEA               NOT NULL,  -- DEK encrypted with KEK (AES-256-GCM)
    dek_nonce     BYTEA               NOT NULL,  -- GCM nonce for the encrypted DEK
    created_at    TIMESTAMP           NOT NULL DEFAULT now()
);
```

### Document types

```sql
CREATE TABLE document_types (
    code        VARCHAR(100) PRIMARY KEY,         -- e.g. 'BULETIN'
    label       VARCHAR(255) NOT NULL,            -- human-readable, e.g. 'Identity Card'
    description TEXT         NOT NULL,            -- used in classify prompt
    prompt_file VARCHAR(255) NOT NULL,            -- e.g. 'buletin.txt'; 'generic.txt' as fallback
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);
```

Add a new document type at any time with a single `INSERT` — no code change needed.

### Documents

```sql
CREATE TABLE documents (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL REFERENCES users(id),
    type                  VARCHAR(100) REFERENCES document_types(code),  -- FK; e.g. 'BULETIN'
    status                VARCHAR(20)  NOT NULL, -- 'processing', 'ready', 'review_needed', 'failed'
    original_filename     VARCHAR(255),
    storage_key           VARCHAR(500),          -- MinIO/S3 key (encrypted file)
    mime_type             VARCHAR(100),
    file_nonce            BYTEA,                 -- GCM nonce for the encrypted file
    extraction_confidence FLOAT,                 -- 0.0 to 1.0
    fields                JSONB,                 -- extracted fields; sensitive values individually encrypted
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_user_id ON documents (user_id);
CREATE INDEX idx_documents_type    ON documents (type);
```

---

## 4b. Entity & Relationship Model

The `documents` table stores raw extracted fields per document but provides no way to consolidate a value (e.g. CNP, address) that appears across multiple documents, or to express domain relationships between real-world objects (person owns vehicle, vehicle covered by policy).

### Tables

```sql
-- Typed containers for real-world objects identified from documents
CREATE TABLE entities (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    type        VARCHAR(50) NOT NULL,   -- PERSON | VEHICLE | LOCATION | ORGANIZATION
    label       VARCHAR(255),           -- human-readable, e.g. "Primary residence"
    attributes  JSONB,                  -- shape varies by type; sensitive values encrypted same as documents.fields
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- Directed, typed edges between entities
CREATE TABLE entity_relations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_entity_id   UUID NOT NULL REFERENCES entities(id),
    to_entity_id     UUID NOT NULL REFERENCES entities(id),
    relationship     VARCHAR(100) NOT NULL,  -- OWNS | LIVES_AT | COVERED_BY | EMPLOYED_BY …
    valid_from       DATE,
    valid_until      DATE,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

-- Provenance: which document field is the source for an entity attribute
CREATE TABLE field_sources (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id        UUID NOT NULL REFERENCES entities(id),
    attribute_key    VARCHAR(100) NOT NULL,  -- e.g. "cnp", "plate_number"
    document_id      UUID NOT NULL REFERENCES documents(id),
    field_path       VARCHAR(255) NOT NULL,  -- jsonb path in documents.fields, e.g. "cnp"
    confidence       FLOAT,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_entities_user_id        ON entities (user_id);
CREATE INDEX idx_entity_relations_from   ON entity_relations (from_entity_id);
CREATE INDEX idx_entity_relations_to     ON entity_relations (to_entity_id);
CREATE INDEX idx_field_sources_entity    ON field_sources (entity_id);
CREATE INDEX idx_field_sources_document  ON field_sources (document_id);
```

### Entity types and attributes

Entity types are fixed (`PERSON`, `VEHICLE`, `LOCATION`, `ORGANIZATION`). The shape of `attributes` varies per type and is validated in application code. Sensitive attribute values (e.g. `cnp`) are encrypted using the same per-value AES-256-GCM pattern as `documents.fields`.

### Consolidation flow

When a document is uploaded and fields are extracted:
1. For each extracted field that maps to an entity attribute (e.g. `cnp` → PERSON), check whether an entity of that type already exists for the user with that attribute value.
2. If yes — add a `field_sources` row linking the new document field to the existing entity.
3. If no — create a new entity, set the attribute, and add a `field_sources` row.
4. Conflicting values across documents (same attribute, different value) are flagged via `confidence` discrepancy for user review.

Domain relationships (e.g. PERSON `OWNS` VEHICLE) are written to `entity_relations` as they are inferred from document context.

---

## 5. Document Input Types

| Input | MIME type | Handling |
|---|---|---|
| Image | `image/*` | Base64-encoded, sent directly to Gemini vision |
| PDF | `application/pdf` | Page 0 → JPEG via PDFBox, then treated as image |
| DOCX | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | Text extracted via Apache POI |
| Plain text | `text/*` | Passed directly as text prompt |

---

## 6. Security Model

Every user's data is encrypted with a key derived from their password. An attacker with full DB + S3 access cannot read documents without the user's password.

### Key hierarchy

```
password ──Argon2id(password, kek_salt)──▶ KEK  (never stored)
                                               │
                                               ▼  AES-256-GCM
                                           DEK  (random 256-bit, generated at registration)
                                               │
                                 ┌─────────────┴──────────────┐
                                 ▼                            ▼
                        encrypt file in MinIO       encrypt sensitive JSONB fields
                        (per-file nonce)            (per-value nonce)
```

- **DEK** — random AES-256 key, generated once at registration. Encrypts all files and sensitive fields. Never stored in plaintext.
- **KEK** — derived from the user's password via Argon2id. Only used to wrap/unwrap the DEK. Never stored.
- **Nonce** — random 96-bit value required by AES-GCM per encryption operation. Not secret; stored alongside the ciphertext. Must be unique per (key, operation) pair.

### At registration
Generate random DEK → derive KEK from password → store `AES-GCM(DEK, key=KEK, nonce=dek_nonce)` in DB.

### At login
Re-derive KEK from password → decrypt DEK → embed DEK as a claim in a JWE-encrypted JWT. The token is opaque to anyone without the server's `JWT_ENCRYPTION_SECRET`.

### Session (JWE)
The DEK travels as an encrypted JWT claim for the duration of the session. Never persisted server-side. Each authenticated request extracts the DEK from the token and uses it inline.

### Sensitive JSONB fields
Fields `cnp`, `iban`, `pret`, `valoare` are encrypted before storage:
```json
{ "ciphertext": "<base64>", "nonce": "<base64>" }
```
Decrypted transparently on read using the DEK from the JWT.

### Password change
Derive new KEK from new password → re-encrypt same DEK → no document re-encryption needed.

### Key loss
If a user forgets their password, their data is unrecoverable by design. A "download recovery key" flow (raw DEK shown once at registration) should be added in Milestone 2.

### Crypto primitives

| Operation | Algorithm | Library |
|---|---|---|
| Password hashing | BCrypt (cost 12) | `at.favre.lib:bcrypt` |
| KEK derivation | Argon2id (m=64 MB, t=3, p=4) | Bouncy Castle |
| File / field encryption | AES-256-GCM | JVM `javax.crypto` |

---

## 7. Auth

Auth is included in Milestone 1. All document endpoints require a JWT. No Cognito — self-managed via Micronaut Security.

### Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | — | Create user, generate DEK |
| `POST` | `/api/auth/login` | — | Verify password, return JWE token |
| `POST` | `/api/documents/upload` | JWT | Upload + extract document |
| `GET` | `/api/documents/{id}` | JWT | Fetch document with decrypted fields |
| `GET` | `/api/documents` | JWT | List user's documents |

### Register — `POST /api/auth/register`
```json
// Request
{ "email": "user@example.com", "password": "..." }

// Response 201
{ "userId": "<uuid>" }
```

### Login — `POST /api/auth/login`
```json
// Request
{ "username": "user@example.com", "password": "..." }

// Response 200
{ "access_token": "<jwe-token>", "token_type": "Bearer" }
```

### Upload — `POST /api/documents/upload`
**Request:** `multipart/form-data`, field `file`

```json
// Response 201
{ "documentId": "<uuid>", "status": "ready" }
```

### Get document — `GET /api/documents/{id}`
```json
{
  "id": "<uuid>",
  "type": "BULETIN",
  "status": "ready",
  "confidence": 0.97,
  "fields": {
    "cnp": "1234567890123",
    "serie": "RX",
    "numar": "123456",
    "nume": "Stoica",
    "prenume": "George Cristian",
    "data_nasterii": "1990-01-15",
    "data_expirarii": "2029-03-15"
  },
  "createdAt": "2026-03-31T10:00:00Z"
}
```

---

## 8. LLM Abstraction

```kotlin
interface DocumentExtractionPort {
    fun extract(content: DocumentContent): ExtractionResult
}

data class DocumentContent(
    val base64Image: String? = null,
    val text: String? = null,
    val mimeType: String,
    val documentType: String? = null,  // null = auto-classify; uppercase code e.g. "BULETIN"
)

data class ExtractionResult(
    val documentType: String,          // uppercase code from document_types.code
    val fields: Map<String, Any?>,
    val confidence: Double,
    val rawResponse: String,
)
```

`GeminiExtractionAdapter` calls the Gemini REST API via Micronaut's HTTP client (avoids SDK reflection issues with GraalVM).

---

## 9. Milestones

| # | Goal | Exit criterion |
|---|---|---|
| **0** ✓ | Prompt validation | >90% field accuracy on 4 document types |
| **1** (now) | Backend MVP — auth + encryption + extraction | Authenticated `curl` upload → encrypted file in MinIO, fields in DB |
| **2** | Web frontend + async extraction (SQS) | 5 non-technical testers scan docs |
| **3** | Q&A, expiry alerts, form auto-fill | User downloads pre-filled PDF form |
| **4** | Payments + private beta | 5 paying users |
| **5** | React Native mobile app | iOS + Android store approval |
| **6** | B2B (insurer partnerships) | First pilot agreement signed |

### Milestone 1 checklist

- [x] Scaffold Micronaut project (Kotlin, KSP, Gradle)
- [x] Docker Compose (PostgreSQL + MinIO)
- [x] Flyway migration: users + documents tables (with encryption columns)
- [x] Flyway migration: `document_types` table + FK from `documents.type`; classify prompt populated dynamically from DB
- [x] `EncryptionService` — BCrypt, Argon2id, AES-256-GCM
- [x] `POST /api/auth/register` — user creation + DEK generation
- [x] `POST /api/auth/login` — credential validation + DEK unwrap + JWE token
- [x] `DocumentExtractionPort` interface + `GeminiExtractionAdapter`
- [x] Extraction prompts (`classify.txt` + 4 document types + `generic.txt`)
- [x] Document conversion pipeline (image / PDF / DOCX / text)
- [x] `POST /api/documents/upload` — encrypt file, extract fields, encrypt sensitive values
- [x] `GET /api/documents/{id}` + `GET /api/documents`
- [ ] Create MinIO bucket on startup if missing
- [ ] Integration test: upload a real buletin JPG → verify fields in DB
- [ ] "Download recovery key" at registration (return raw DEK once, base64-encoded)

**Exit criterion:** `curl` register → login → upload `buletin.jpg` → extracted fields returned, file encrypted in MinIO, fields in DB.

### Milestone 2

- [ ] Web frontend (React or Next.js)
- [ ] Async extraction via SQS (local: LocalStack); decouple upload from extraction
- [ ] WebSocket or polling for extraction status
- [ ] Multi-page PDF handling
- [ ] CNP checksum validation
- [ ] "Download recovery key" flow
- [ ] Deploy to AWS Lambda (GraalVM native image)

---

## 10. Constraints

- **GDPR / sensitive data** — documents contain Romanian PII (CNP, financial, property data). Field-level encryption required before persist. Data must remain in AWS `eu-central-1`.
- **CNP validation** — the 13-digit Romanian personal ID has a checksum algorithm; validate before storing (deferred to Milestone 2).
- **GraalVM compatibility** — avoid reflection-heavy libraries without GraalVM config; Micronaut compile-time DI is chosen specifically for this.
- **First page only (Phase 1)** — multi-page PDF handling deferred to Milestone 2.
- **No Cognito** — self-managed JWT auth (Micronaut Security).

---

*Last updated: March 2026.*
