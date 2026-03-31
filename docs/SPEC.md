# DocuRo — Technical Exploration

> Working notes for the early build phases. The full product vision lives in SPEC.md.

---

## 1. What We're Building First

A backend service (Micronaut + Kotlin) that:
1. Accepts a document upload (docx, pdf, or photo)
2. Converts it to a format the LLM can read
3. Sends it to Gemini 2.5 Flash-Lite for field extraction
4. Stores the original document in local S3-compatible storage (MinIO)
5. Persists the user, document metadata, and extracted fields to PostgreSQL

No frontend yet. Tested via curl or a simple HTTP client.

---

## 2. Tech Stack

### Backend

| Layer | Technology | Rationale |
|---|---|---|
| Language | Kotlin | Concise, null-safe, idiomatic on JVM, natural fit with Micronaut |
| Framework | Micronaut | Designed for serverless/Lambda, fast cold starts, compile-time DI |
| Runtime | JVM (dev) → GraalVM native (prod Lambda) | Fast iteration locally, near-zero cold starts in production |
| Build | Gradle (Kotlin DSL) | Standard for Kotlin projects |

### Data Layer

| Layer | Technology | Rationale |
|---|---|---|
| Database | PostgreSQL | JSONB column handles flexible per-document-type field schemas |
| Document fields | JSONB column in Postgres | No need for MongoDB — Postgres JSONB is queryable, indexable, sufficient |
| Object storage (local) | MinIO | S3-compatible API, runs as Docker container, zero code change when moving to AWS S3 |
| Object storage (prod) | AWS S3 | Same SDK, same code — swap endpoint URL only |

### Why PostgreSQL + JSONB over MongoDB or DynamoDB

MongoDB adds operational complexity (separate service, different query language) without meaningful benefit at this scale. DynamoDB is AWS-specific and requires a different mental model for querying. PostgreSQL with JSONB gives you:

```sql
-- Store any document type's fields in one table
SELECT fields->>'cnp', fields->>'serie'
FROM documents
WHERE user_id = $1 AND type = 'buletin';

-- Index a specific field for fast lookup
CREATE INDEX idx_documents_cnp ON documents ((fields->>'cnp'));
```

This is sufficient for MVP and well into production scale. Revisit only if you hit genuine query performance issues at tens of thousands of users.

### LLM

| Task | Model | Notes |
|---|---|---|
| Document type classification | `gemini-flash-lite-latest` | Free tier sufficient for dev/testing |
| Field extraction | `gemini-flash-lite-latest` | ~$0.00013 per document at paid tier |
| Q&A over extracted data (Phase 2) | `gemini-flash-lite-latest` | Queries stored JSON, not images |

All LLM calls go through a `DocumentExtractionPort` interface so the model can be swapped via config without touching business logic.

---

## 3. Local Development Environment

Everything runs locally via Docker Compose. No AWS account needed for Phase 1.

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16
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
      - "9001:9001"   # MinIO console (browser UI)
    volumes:
      - minio_data:/data

volumes:
  minio_data:
```

Start with: `docker compose up -d`
MinIO console available at: `http://localhost:9001`

---

## 4. Data Model

### Users

```sql
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,  -- bcrypt
    created_at    TIMESTAMP NOT NULL DEFAULT now()
);
```

### Documents

```sql
CREATE TABLE documents (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id),
    type                    VARCHAR(50),           -- 'buletin', 'rca', 'permis', 'unknown'
    status                  VARCHAR(20) NOT NULL,  -- 'processing', 'ready', 'review_needed', 'failed'
    original_filename       VARCHAR(255),
    storage_key             VARCHAR(500),          -- MinIO/S3 object key
    mime_type               VARCHAR(100),          -- 'image/jpeg', 'application/pdf', etc.
    extraction_confidence   FLOAT,                 -- 0.0 to 1.0
    fields                  JSONB,                 -- extracted key-value pairs, type-specific
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);

-- Index for fast user document lookups
CREATE INDEX idx_documents_user_id ON documents (user_id);

-- Index for querying inside JSONB fields (add per field as needed)
CREATE INDEX idx_documents_type ON documents (type);
```

### Example stored document record

```json
{
  "id": "a1b2c3d4-...",
  "user_id": "u1u2u3u4-...",
  "type": "buletin",
  "status": "ready",
  "storage_key": "users/u1u2u3u4/documents/a1b2c3d4.jpg",
  "extraction_confidence": 0.97,
  "fields": {
    "cnp": "1234567890123",
    "serie": "RX",
    "numar": "123456",
    "nume": "Stoica",
    "prenume": "George Cristian",
    "data_nasterii": "1990-01-15",
    "domiciliu": "Str. Exemplu nr. 1, Sector 1, București",
    "data_expirarii": "2029-03-15",
    "emisa_de": "SPCLEP Sector 1"
  }
}
```

---

## 5. Document Input Types & Handling

The service must handle three input categories before sending to the LLM:

| Input type | Extensions | Handling strategy |
|---|---|---|
| Photo | jpg, jpeg, png, heic, webp | Send directly as base64 image to Gemini vision |
| PDF | pdf | Convert each page to image (Apache PDFBox), send page 1 (or all pages for contracts) |
| Word document | docx | Extract text via Apache POI; for image-heavy docs, convert to PDF first |

### Conversion pipeline

```
Uploaded file
      ↓
Detect MIME type
      ↓
┌─────────────────────────────────────┐
│ image/* → encode as base64          │
│ application/pdf → PDFBox → images   │
│ application/vnd.openxml → POI text  │
│   (or → PDF → images if needed)     │
└─────────────────────────────────────┘
      ↓
Send to Gemini Flash-Lite
      ↓
Receive structured JSON
      ↓
Persist to PostgreSQL + store original in MinIO
```

---

## 6. API Endpoints (Phase 1)

All endpoints are unauthenticated in Phase 1 — auth comes in Phase 2.

### POST /api/documents/upload

Upload a document and trigger extraction.

**Request:** `multipart/form-data`
- `file` — the document file (jpg, png, pdf, docx)
- `userId` — UUID of the user (hardcoded for now, replaced by JWT in Phase 2)

**Response:**
```json
{
  "documentId": "a1b2c3d4-...",
  "status": "processing"
}
```

Extraction is synchronous in Phase 1 (no queue yet). Response returns after extraction completes.

### GET /api/documents/{documentId}

Fetch a document with its extracted fields.

**Response:**
```json
{
  "id": "a1b2c3d4-...",
  "type": "buletin",
  "status": "ready",
  "confidence": 0.97,
  "fields": {
    "cnp": "1234567890123",
    "serie": "RX",
    ...
  },
  "createdAt": "2026-03-27T10:00:00Z"
}
```

### GET /api/documents?userId={userId}

List all documents for a user.

---

## 7. LLM Abstraction Layer

All LLM calls go through a port interface. This ensures the model can be swapped without changing business logic.

```kotlin
// Port — business logic only depends on this
interface DocumentExtractionPort {
    fun extract(content: DocumentContent): ExtractionResult
}

data class DocumentContent(
    val base64Image: String? = null,  // for image/pdf inputs
    val text: String? = null,          // for docx text extraction
    val mimeType: String,
    val documentType: DocumentType? = null  // null = auto-classify
)

data class ExtractionResult(
    val documentType: DocumentType,
    val fields: Map<String, Any?>,
    val confidence: Double,
    val rawResponse: String
)

// Adapter — the actual Gemini implementation
class GeminiExtractionAdapter(
    private val apiKey: String,
    private val model: String = "gemini-flash-lite-latest"
) : DocumentExtractionPort {
    override fun extract(content: DocumentContent): ExtractionResult {
        // call Gemini API here
    }
}
```

Configured in `application.yml`:
```yaml
docuro:
  llm:
    provider: gemini          # swap to 'openai' or 'mock' for tests
    model: gemini-flash-lite-latest
    api-key: ${GEMINI_API_KEY}
```

---

## 8. Extraction Prompts

Prompts live in `/src/main/resources/prompts/` as plain text files, one per document type. This makes them easy to edit without recompiling.

```
prompts/
  classify.txt           ← document type classification prompt
  buletin.txt            ← buletin field extraction prompt
  permis_conducere.txt
  polita_rca.txt
  certificat_inmatriculare.txt
  generic.txt            ← fallback for unknown document types
```

Each extraction prompt instructs the model to return strict JSON matching the schema in SPEC.md Section 5. Example structure for `buletin.txt`:

```
You are extracting fields from a Romanian Carte de Identitate (buletin).

Extract the following fields and return ONLY valid JSON, no other text:
- cnp: 13-digit personal identification number
- serie: 2 uppercase letters (e.g. "RX")
- numar: 6 digits
- nume: family name
- prenume: first name(s)
- data_nasterii: date of birth in ISO format (YYYY-MM-DD)
- locul_nasterii: place of birth
- domiciliu: full address as printed
- data_emiterii: issue date (YYYY-MM-DD)
- data_expirarii: expiry date (YYYY-MM-DD)
- emisa_de: issuing authority (e.g. "SPCLEP Sector 1")

If a field is not visible or unclear, return null for that field. Never guess.
Return confidence as a float 0.0-1.0 indicating overall extraction quality.

Return format:
{
  "type": "buletin",
  "confidence": 0.95,
  "fields": { ... }
}
```

---

## 9. Phase 1 Milestones

### Milestone 0 — Prompt Validation (current)
*Test LLM extraction quality before writing backend code.*

- [ ] Set up Python test script with `google-genai`
- [ ] Test classification prompt against 5+ document types
- [ ] Test extraction prompt for buletin → benchmark accuracy
- [ ] Test extraction prompt for poliță RCA → benchmark
- [ ] Test extraction prompt for permis de conducere → benchmark
- [ ] Test extraction prompt for certificat înmatriculare → benchmark
- [ ] Document results and final prompt versions in `/prompts` folder

**Exit criterion:** >90% field accuracy on all 4 document types via Python script.

---

### Milestone 1 — Backend MVP
*Working Micronaut + Kotlin service, tested via curl.*

- [ ] Scaffold Micronaut project (Kotlin, Gradle)
- [ ] Set up Docker Compose (PostgreSQL + MinIO)
- [ ] Flyway migrations: users + documents tables
- [ ] MinIO client: upload file, generate storage key
- [ ] Document conversion pipeline (image passthrough, PDF→image via PDFBox, docx text via POI)
- [ ] `DocumentExtractionPort` interface + `GeminiExtractionAdapter`
- [ ] Load prompts from `/resources/prompts/` per document type
- [ ] `POST /api/documents/upload` endpoint (synchronous extraction)
- [ ] `GET /api/documents/{id}` endpoint
- [ ] `GET /api/documents?userId=` endpoint
- [ ] Integration test: upload a real buletin jpg → verify fields in DB

**Exit criterion:** `curl -F file=@buletin.jpg http://localhost:8080/api/documents/upload` returns correct extracted fields within 10 seconds.

---

### Milestone 2 — Async Extraction + Auth (next phase)

- [ ] Add SQS (local: ElasticMQ or LocalStack) for async extraction jobs
- [ ] Decouple upload (returns immediately) from extraction (background worker)
- [ ] WebSocket or polling endpoint for extraction status
- [ ] Self-managed JWT auth via Micronaut Security (bcrypt passwords, no Cognito)
  - [ ] `POST /api/auth/register` — create user with hashed password
  - [ ] `POST /api/auth/login` — validate credentials, return JWT
  - [ ] Secure all document endpoints behind JWT; extract `userId` from token
- [ ] Deploy to AWS Lambda (Micronaut native image via GraalVM)

---

## 10. Open Questions (Phase 1 Scope)

| Question | Decision |
|---|---|
| Auth in Phase 1? | No — hardcode userId, add self-managed JWT (Micronaut Security + bcrypt) in Milestone 2. No Cognito. |
| Async queue in Phase 1? | No — synchronous extraction, add SQS in Milestone 2 |
| Multi-page PDF handling? | Phase 1: first page only. Multi-page in Milestone 2 |
| HEIC photo support? | Defer — convert to JPG on mobile before upload |
| Field validation (CNP checksum)? | Phase 1: store raw LLM output. Validation layer in Milestone 2 |
| Which Micronaut HTTP client for Gemini? | `micronaut-http-client` or plain OkHttp — decide during implementation |

---

*Last updated: March 2026.*
