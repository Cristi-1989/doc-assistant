# DocuRo — Product Specification

> *"Scanezi o dată, folosești oriunde."* — A Romanian-first intelligent document wallet that extracts, structures, and acts on your personal data across all your documents.

---

## 1. Overview

### What It Is

DocuRo is a mobile-first application that lets Romanian users photograph any personal document — from a buletin to a complex property contract — and automatically extracts, structures, and stores the key data fields. Users get a single place to query their own data, receive expiry alerts, auto-fill insurance and government forms using their stored data, and (in later phases) selectively share data with third parties like insurance companies or banks.

The core loop: **scan once → use everywhere.** Every form, every institution, every renewal that asks for data you already have — DocuRo fills it in.

### The Problem It Solves

Romanians manage a fragmented landscape of physical and digital documents: CNP, serie/număr buletin, CUI for PFAs, carte funciară, contracte de vânzare-cumpărare, polițe RCA/CASCO, asigurări locuință, contracte de utilități, and more. Finding a specific field (e.g. suprafața utilă from a property contract, or the prima de asigurare from an insurance policy) requires hunting through physical folders or unsorted photo albums. This creates real friction in everyday life and especially in formal contexts (bank visits, insurance renewals, notary appointments).

### Why Romania-First

Generic document apps (AskYourPDF, Adobe Scan, etc.) treat all documents as generic PDFs. DocuRo knows what a buletin looks like. It knows the difference between suprafață utilă and suprafață construită. It validates CNP checksums. It recognizes RCA policy templates from major Romanian insurers. This local specificity is the core technical moat and cannot be easily replicated by global players.

---

## 2. Target Market

### Primary: Romanian individual consumers

- Urban, 25–45 years old
- Smartphone-native, comfortable with apps like Revolut, Tazz, eMAG
- Owns property or a car, has insurance, may have a PFA
- Pain point: wastes time hunting for document details in high-stakes moments (signing contracts, dealing with insurance, visiting a notary)

### Secondary: PFA / micro-entrepreneur

- Juggles CUI, multiple contracts, leasing documents, facturi
- Regularly shares data with accountants, clients, notaries
- Already pays for productivity tools
- Higher willingness to pay than the average consumer

### Future (Phase 3): Insurance companies & banks (B2B)

- Need accurate personal data during onboarding and claims
- Currently rely on manual data entry by agents or customers
- Pain point: data quality, collection cost, onboarding friction

---

## 3. Business Model

### Freemium Tiers

| Tier | Price | What You Get |
|---|---|---|
| Free | 0 RON | 1 document scan with full field extraction (demo) |
| Personal | ~20 RON/month | Unlimited scans, full Q&A, expiry alerts, cross-document intelligence |
| Pro (PFA/SRL) | ~50 RON/month | Everything in Personal + multiple business profiles, accountant data export |
| B2B (Insurer/Bank) | Negotiated | API access to user-consented data flows, per-completed-share pricing |

### Conversion Strategy

The free scan must create an immediate "wow" moment. Guide users toward document types with highest extraction confidence for their first scan (buletin, RCA policy). Do not let the first experience be the worst-performing document type.

### B2B Monetization Logic

Modelled on open banking consent flows:
1. User initiates a quote/onboarding on an insurer's website
2. Gets prompted to connect their DocuRo account
3. Selects which fields to share (app suggests the right ones automatically)
4. Data flows directly, pre-validated, to the insurer
5. DocuRo charges the insurer per successful data share or per completed onboarding

---

## 4. Core Features by Phase

### Phase 1 — MVP (Document Scanning & Extraction)

- [ ] Camera capture or gallery upload of document photos
- [ ] Document type detection (auto-classify: buletin, permis auto, RCA, etc.)
- [ ] Structured field extraction via Vision LLM (returns typed JSON per document type)
- [ ] Secure encrypted storage of extracted fields + original image
- [ ] User dashboard: list of documents with key fields visible at a glance
- [ ] Expiry alerts (buletin, permis, RCA, passport)
- [ ] Basic search: "show me my CNP", "what is my număr carte funciară"
- [ ] Freemium gate: 1 free scan, paywall for additional scans

### Phase 2 — Q&A & Cross-Document Intelligence

- [ ] Natural language Q&A over extracted data ("what's the surface area of my apartment?")
- [ ] Cross-document insights ("your car insurance expires in 23 days")
- [ ] Document health dashboard: missing documents, expiring soon, incomplete extractions
- [ ] Intelligent suggestions: "You have a property contract but no asigurare locuință linked"
- [ ] Multi-document queries ("what's the total insured value across all my policies?")

### Phase 2b — Form Auto-Fill (B2C)

This phase transforms DocuRo from a passive document store into an active assistant. Two distinct form-fill flows, each with different implementation strategies.

#### Insurance Quote Auto-Fill

Target: RCA, CASCO, asigurare locuință forms on platforms like eMAG Broker, Groupama online, Allianz online, Asirom.

The typical RCA quote form asks for: CNP, serie/număr buletin, număr înmatriculare, serie VIN, marcă/model, an fabricație, capacitate cilindrică, putere, adresă proprietar — all of which live in DocuRo already (buletin + certificat înmatriculare).

**Implementation approach — two tracks:**

*Track A (B2C, no partnership needed):* In-app browser with JavaScript field injection. User taps "Completează automat" inside the DocuRo browser, and the app maps extracted fields to known form field names per insurer. Fragile long-term (forms change) but shippable in days and creates immediate user value.

*Track B (B2B partnership, preferred long-term):* "Completează cu DocuRo" button embedded on the insurer's site — an OAuth-style consent flow where the user approves which fields to share and data flows directly via API. This is the conversion optimization pitch to insurers: users complete quote forms in 30 seconds instead of 4 minutes, reducing drop-off. DocuRo charges per completed share.

Track A ships first for user validation. Track B is the B2B pivot conversation.

- [ ] Map DocuRo fields to RCA form fields for top 3 Romanian online platforms
- [ ] In-app browser with "Completează automat" trigger (Track A)
- [ ] Field mapping registry (JSON config per insurer, easy to update without deploy)
- [ ] "Fill with DocuRo" OAuth consent flow spec + partner API (Track B, Phase 3)

#### Government & ANAF Form Auto-Fill

Target: fiscal declaration forms required after purchasing property or a vehicle, plus common ANAF/local council forms.

**Key forms to support at launch:**

| Form | Trigger event | DocuRo fields used |
|---|---|---|
| Declarație de impunere — imobil (ITL-001) | Bought a property | CNP, adresă, nr. cadastral, suprafață utilă, suprafață construită, valoare, data dobândirii |
| Declarație de impunere — mijloc de transport (ITL-004) | Bought a vehicle | CNP, adresă, marcă, model, an fabricație, capacitate cilindrică, putere, valoare |
| Declarație 010 ANAF | PFA registration change | CUI, denumire, sediu, cod CAEN |

**Implementation approach — pre-filled PDF generation (no ANAF API needed):**

Do not attempt to integrate with ghișeul.ro or ANAF systems directly — their APIs are inconsistent across judete and largely unavailable. Instead, generate a pre-filled PDF that the user prints and submits physically or uploads to ghișeul.ro manually. This sidesteps all government API complexity while still saving the user significant time — they currently fill these forms by hand.

Process:
1. User indicates a life event ("Am cumpărat un imobil")
2. DocuRo identifies the relevant form(s) and which documents supply which fields
3. User reviews the pre-filled data and confirms
4. DocuRo generates a print-ready PDF with all fields populated
5. User prints and takes to ghișeu, or uploads to ghișeul.ro

- [ ] PDF template library for ITL-001, ITL-004, Declarație 010
- [ ] Life event triggers: "Am cumpărat imobil", "Am cumpărat mașină", "Am înregistrat PFA"
- [ ] Field mapping: DocuRo JSON fields → form field positions in PDF template
- [ ] PDF generation service (Apache PDFBox in Micronaut Lambda)
- [ ] "Ce acte îți trebuie" checklist per life event (e.g. also remind about RCA after buying a car)
- [ ] Future: direct upload to ghișeul.ro when API becomes available

### Phase 3 — B2B Integrations & Data Sharing

- [ ] "Completează cu DocuRo" OAuth-style consent flow for insurers (Track B from Phase 2b)
- [ ] Partner API: insurer requests a field set → user approves in-app → data flows pre-validated
- [ ] Integration with at least one major Romanian insurer (Allianz Tiriac, Groupama, or eMAG Broker)
- [ ] Intelligent pre-fill suggestions when a share request is received
- [ ] Per-completed-share pricing model with insurer partners
- [ ] Audit log: user can see who accessed what data and when
- [ ] ANAF public API cross-reference: CUI validation, TVA status lookup
- [ ] Future: direct form submission to ghișeul.ro when government APIs become available

---

## 5. Supported Document Types & Extracted Fields

Each document type has a defined JSON schema. This schema is the core data asset of the product.

### 5.1 Carte de Identitate (Buletin)

```json
{
  "type": "buletin",
  "fields": {
    "cnp": "string (13 digits, checksum-validated)",
    "serie": "string (2 letters)",
    "numar": "string (6 digits)",
    "nume": "string",
    "prenume": "string",
    "data_nasterii": "date",
    "locul_nasterii": "string",
    "domiciliu": "string",
    "data_emiterii": "date",
    "data_expirarii": "date",
    "emisa_de": "string (e.g. SPCLEP Sector 1)"
  }
}
```

### 5.2 Permis de Conducere

```json
{
  "type": "permis_conducere",
  "fields": {
    "numar": "string",
    "nume": "string",
    "prenume": "string",
    "data_nasterii": "date",
    "data_emiterii": "date",
    "data_expirarii": "date",
    "categorii": ["B", "A", ...],
    "emis_de": "string"
  }
}
```

### 5.3 Poliță RCA

```json
{
  "type": "polita_rca",
  "fields": {
    "numar_polita": "string",
    "asigurator": "string",
    "nume_asigurat": "string",
    "cnp_asigurat": "string",
    "marca_auto": "string",
    "model_auto": "string",
    "numar_inmatriculare": "string",
    "serie_vin": "string",
    "data_inceput": "date",
    "data_sfarsit": "date",
    "prima_de_asigurare": "number (RON)",
    "limita_raspundere_persoane": "number",
    "limita_raspundere_bunuri": "number"
  }
}
```

### 5.4 Contract Vânzare-Cumpărare Imobil

```json
{
  "type": "contract_vanzare_cumparare",
  "fields": {
    "numar_contract": "string",
    "data_contract": "date",
    "notar": "string",
    "vanzator_nume": "string",
    "cumparator_nume": "string",
    "adresa_imobil": "string",
    "numar_carte_funciara": "string",
    "numar_cadastral": "string",
    "suprafata_utila": "number (mp)",
    "suprafata_construita": "number (mp)",
    "suprafata_teren": "number (mp, if applicable)",
    "pret_vanzare_ron": "number",
    "pret_vanzare_eur": "number",
    "etaj": "string",
    "numar_camere": "number"
  }
}
```

### 5.5 Certificat de Înmatriculare Auto

```json
{
  "type": "certificat_inmatriculare",
  "fields": {
    "numar_inmatriculare": "string",
    "serie_vin": "string",
    "marca": "string",
    "model": "string",
    "an_fabricatie": "number",
    "culoare": "string",
    "capacitate_cilindrica": "number (cm³)",
    "putere": "number (kW)",
    "masa_maxima": "number (kg)",
    "numar_locuri": "number",
    "proprietar_nume": "string",
    "proprietar_adresa": "string"
  }
}
```

### 5.6 CUI / Certificat Înregistrare PFA / SRL

```json
{
  "type": "certificat_inregistrare",
  "fields": {
    "cui": "string",
    "denumire": "string",
    "forma_juridica": "string (PFA, SRL, II, etc.)",
    "sediu_social": "string",
    "data_inregistrarii": "date",
    "numar_ordine_registru": "string",
    "cod_caen_principal": "string",
    "stare": "string (activ/inactiv)"
  }
}
```

### 5.7 Additional Document Types (Future)

- Poliță CASCO
- Asigurare locuință
- Contract de închiriere
- Certificat de urbanism
- Autorizație de construire
- Pașaport
- Card de sănătate / asigurare CNAS
- Contract utilități (curent, gaz, apă)
- Factură / chitanță (for PFA expense tracking)

---

## 6. Technical Architecture

### 6.1 System Overview

```
┌─────────────────────────────────────────────────────┐
│              React Frontend                          │
│         (S3 + CloudFront, ~$1-2/month)              │
└──────────────────────┬──────────────────────────────┘
                       │ HTTPS
┌──────────────────────▼──────────────────────────────┐
│                  API Gateway                         │
└───────────┬──────────────────────┬──────────────────┘
            │                      │
┌───────────▼──────────┐  ┌────────▼───────────────────┐
│   Query Lambda        │  │   Upload Lambda             │
│   (Quarkus native)    │  │   (Quarkus native)          │
│                       │  │                             │
│   - Q&A over fields   │  │   - Receives image          │
│   - Document list     │  │   - Validates type/size     │
│   - User account      │  │   - Stores raw image to S3  │
│   - Auth/JWT          │  │   - Drops job on SQS queue  │
└───────────┬───────────┘  └────────┬───────────────────┘
            │                       │
            │              ┌────────▼───────────────────┐
            │              │   Extraction Lambda          │
            │              │   (Quarkus native)           │
            │              │   Triggered by SQS           │
            │              │                              │
            │              │   - Reads image from S3      │
            │              │   - Classifies doc type      │
            │              │   - Calls Vision LLM         │
            │              │   - Validates extracted fields│
            │              │   - Persists JSON to DB      │
            │              │   - Notifies user (WS/push)  │
            │              └────────┬───────────────────┘
            │                       │
┌───────────▼───────────────────────▼───────────────────┐
│                      Data Layer                         │
│   Aurora Serverless v2 PostgreSQL                       │
│   (scales to zero when idle — key for MVP cost)         │
│   S3  (encrypted original document images, private)     │
│   SQS (extraction job queue)                            │
│   AWS KMS (per-user encryption key management)          │
└─────────────────────────────────────────────────────────┘
```

### 6.2 Why Micronaut + Kotlin over Spring Boot + Fargate

Spring Boot cold starts on Lambda are 3–8 seconds, which is unacceptable for interactive Q&A. Fargate avoids cold starts but charges 24/7 whether users are active or not — expensive at MVP traffic levels.

Micronaut with GraalVM native compilation gives ~20–50ms cold starts on Lambda, and Lambda pricing means you pay only for actual invocations. At MVP scale (tens to low hundreds of users) this is significantly cheaper than always-warm Fargate containers.

Kotlin is the primary language — concise, null-safe, and idiomatic on the JVM with excellent Micronaut support.

**Dev workflow note:** During development, run Micronaut in JVM mode for fast iteration. Native compilation (5–10 min build) only happens at deployment time via CI/CD.

### 6.3 Async Extraction Flow

Document upload and LLM extraction are decoupled via SQS. The user never waits synchronously for extraction to complete.

```
1. User uploads photo → Upload Lambda responds immediately with jobId
2. Upload Lambda stores image in S3, drops message on SQS
3. User sees "Processing..." in the UI
4. Extraction Lambda picks up SQS message (triggered automatically)
5. Calls Vision LLM → gets structured JSON → validates fields
6. Persists to Aurora, deletes SQS message
7. Pushes notification to user: "Your buletin is ready"
```

If extraction fails (LLM error, low confidence), the message goes to a Dead Letter Queue (DLQ) for inspection. User sees a "review needed" flag on the document.

### 6.4 LLM Stack

| Task | Model | Rationale |
|---|---|---|
| Document type classification | Gemini 2.0 Flash | Cheapest vision model, fast, sufficient for classification |
| Field extraction — standard docs | Gemini 2.0 Flash | ~€0.00015/image, good quality on structured docs |
| Field extraction — complex contracts | GPT-4o | Fallback for dense Romanian legal text |
| Q&A over extracted data | GPT-4o mini or Claude Haiku | Queries structured JSON only, not images — very cheap |

**Critical principle:** Extract once with the Vision LLM, store structured JSON. All Q&A runs against the stored JSON — never re-processes images. This keeps ongoing cost negligible after initial extraction.

There is no model training or fine-tuning. Accuracy comes from prompt engineering — carefully crafted, document-type-specific extraction prompts tested against real Romanian documents. See Section 11 (Prompt Engineering) for the approach.

### 6.5 Estimated Cost Per User Per Month

| Item | Estimated Cost |
|---|---|
| Initial 20 document extractions (onboarding) | ~€0.05 |
| 2–3 new scans/month (ongoing) | ~€0.01 |
| ~20 Q&A queries/month | ~€0.02 |
| **Total LLM cost/user/month** | **~€0.08** |

At ~20 RON/month (~€4) subscription price, LLM cost is under 2% of revenue per paying user.

### 6.6 Infrastructure Cost (MVP)

| Component | Service | Est. Monthly Cost |
|---|---|---|
| Frontend | S3 + CloudFront | ~$1 |
| API Gateway | AWS API Gateway | ~$1 |
| Query + Upload Lambdas | Lambda (Quarkus native) | ~$0–2 (free tier covers MVP) |
| Extraction Lambda | Lambda + SQS trigger | ~$0–2 |
| Database | Aurora Serverless v2 | ~$5–10 (scales to near zero) |
| Document storage | S3 | ~$2 |
| Encryption | AWS KMS | ~$1 |
| LLM APIs | Gemini + GPT-4o | ~$0–5 |
| **Total** | | **~$10–25/month** |

Break-even: approximately 20–30 paying users at 20 RON/month.

### 6.7 Data Model (High Level)

```
User
  ├── id (UUID)
  ├── email
  ├── kms_key_id (per-user AWS KMS key for field encryption)
  ├── created_at
  └── Documents[]
        ├── id (UUID)
        ├── type (enum: buletin | permis | rca | contract_vc | ...)
        ├── status (enum: processing | ready | review_needed | failed)
        ├── created_at
        ├── s3_key (private, never exposed as public URL)
        ├── extraction_confidence (float 0–1)
        └── fields{} (JSONB — type-specific, encrypted at rest)

ExtractionJob
  ├── id
  ├── document_id
  ├── sqs_message_id
  ├── status
  ├── llm_model_used
  ├── attempts
  └── error_detail (if failed)

ConsentShare (Phase 3)
  ├── user_id
  ├── recipient_id (insurer/bank)
  ├── fields_shared[]
  ├── shared_at
  ├── expires_at
  └── revoked_at
```

### 6.8 Tech Stack Summary

| Layer | Technology |
|---|---|
| Frontend | React + Vite (web MVP); React Native later for mobile |
| Language | Kotlin |
| Backend Lambdas | Micronaut (GraalVM native), Kotlin |
| Database | Aurora Serverless v2 PostgreSQL (JSONB for document fields) |
| Object storage (local) | MinIO (S3-compatible, Docker) |
| Object storage (prod) | AWS S3 (eu-central-1, private buckets) |
| Queue | AWS SQS (standard queue + DLQ) |
| Auth | AWS Cognito (handles JWT, refresh, social login) |
| Encryption | AWS KMS (per-user keys) + AES-256 at rest |
| LLM APIs | Google AI (Gemini 2.5 Flash-Lite primary, GPT-4o fallback) |
| Hosting region | eu-central-1 (Frankfurt) — GDPR data residency |
| CI/CD | GitHub Actions (native build + Lambda deploy) |

### 6.9 Database Choice Rationale

Document fields are stored as `JSONB` in PostgreSQL — no MongoDB or DynamoDB needed. Each document type has a different field schema, and JSONB handles this cleanly in a single `documents` table. Fields are queryable and indexable. This decision should be revisited only if query performance degrades beyond tens of thousands of users.

For local development, MinIO replaces AWS S3 with a fully S3-compatible API. No code changes are needed when deploying to production — only the endpoint URL changes.

---

## 7. Security & GDPR

This section is non-negotiable and must be addressed before any user data is stored.

### 7.1 Data Classification

All user document data is **sensitive personal data** under GDPR Article 9 (financial, identity, property). This requires:
- Explicit, informed consent at signup (not buried in ToS)
- Purpose limitation: data used only for user-requested features
- Data minimization: store only extracted fields + original image; no unnecessary metadata
- Right to erasure: full account + data deletion must be possible within 30 days

### 7.2 Security Requirements

- All data encrypted at rest (AES-256) and in transit (TLS 1.3)
- Original document images stored in private object storage (no public URLs)
- Extracted JSON fields encrypted in database (field-level encryption for CNP, financial data)
- Biometric authentication required to access sensitive documents on mobile
- No third-party analytics SDKs with access to document content
- Penetration testing before B2B launch
- Data residency in EU (required for Romanian users under GDPR)

### 7.3 Consent Model for Data Sharing (Phase 3)

- User must explicitly approve each share, field by field
- Share is time-limited (user sets expiry or single-use)
- Full audit log stored and visible to user
- Recipient (insurer/bank) cannot re-share or store beyond the stated purpose
- Consent can be revoked at any time

---

## 8. Non-Functional Requirements

| Requirement | Target |
|---|---|
| Extraction latency | < 5 seconds for standard documents |
| Extraction accuracy | > 95% field accuracy on supported document types |
| App startup time | < 2 seconds cold start |
| Uptime | 99.5% (MVP), 99.9% (post-B2B launch) |
| GDPR compliance | Full, before any user data is stored |
| Language | Romanian UI primary; English secondary |
| Platform | iOS 15+ and Android 10+ |

---

## 9. Go-To-Market

### Phase 1 Launch Strategy

- Target early adopters via Romanian tech communities (Doers.ro, Facebook groups for PFA owners, Reddit r/Romania)
- Product Hunt launch (Romanian angle as differentiator)
- Content marketing: "Unde îți ții documentele?" — relatable pain point content
- Referral program: share a free scan credit with a friend

### Validation Milestones Before Each Phase

| Before Phase | Milestone |
|---|---|
| Phase 1 launch | 10 Romanians tested prototype, extraction accuracy > 90% on buletin + RCA |
| Phase 2 build | 100 paying users, average 3+ Q&A queries/week per user |
| Phase 3 build | 1,000 active users, informal commitment from 1 insurer to pilot |

---

## 10. Prompt Engineering Strategy

This is where the product is validated before any infrastructure is built. The goal is to reach >95% field extraction accuracy on the 4 launch document types using prompt engineering alone — no fine-tuning, no custom models.

### 10.1 Approach

Every document type gets two prompts:

**Prompt 1 — Classification prompt** (used once per upload, cheap)
```
Given this document image, identify which Romanian document type it is.
Return only one of: buletin | permis_conducere | polita_rca | 
certificat_inmatriculare | contract_vanzare_cumparare | 
certificat_inregistrare | unknown

Return JSON: { "type": "...", "confidence": 0.0-1.0 }
```

**Prompt 2 — Type-specific extraction prompt** (used after classification)
Each document type has its own detailed prompt that includes:
- Exact field names to extract (matching the JSON schema in Section 5)
- Romanian-specific context ("CNP is a 13-digit personal identifier")
- Validation hints ("seria is always 2 uppercase letters followed by 6 digits")
- Handling instructions for missing/unclear fields ("return null, never guess")
- Output format: strict JSON matching the schema

### 10.2 Evaluation Dataset

Before writing any backend code, build a ground truth dataset:

| Document Type | Target Sample Size | Source |
|---|---|---|
| Buletin | 5–10 samples | Your own + friends/family (anonymized) |
| Permis de conducere | 5–10 samples | Same |
| Poliță RCA | 10–15 samples | Multiple insurers (Allianz, Groupama, Asirom templates differ) |
| Certificat înmatriculare | 5–10 samples | Same |

For each sample, manually record the correct field values. This becomes your benchmark.

### 10.3 Benchmarking Process

Run each document through Gemini 2.0 Flash and GPT-4o with your prompts. Score per field:
- **Exact match** = 1.0
- **Minor formatting difference** (date format, spacing) = 0.8
- **Wrong value** = 0.0
- **Null when value exists** = 0.0

Track accuracy per field, per document type, per model. This tells you exactly where prompts need work and which model wins on which document type.

### 10.4 Iteration Loop

```
Write prompt → run against dataset → score results
      ↑                                     ↓
 Refine prompt  ←─── identify weak fields ──┘
```

Typical weak spots to watch for on Romanian documents:
- Date formats (RCA policies mix dd.mm.yyyy and dd/mm/yyyy)
- Diacritics (ș vs ş, ț vs ţ — old vs new Romanian encoding)
- Multi-page contracts where key fields are spread across pages
- Handwritten notarial stamps overlapping printed text

### 10.5 Confidence Scoring

The LLM returns a confidence score per field. Define thresholds:
- `>= 0.9` → auto-accept, display to user
- `0.7–0.9` → display with yellow "please verify" indicator
- `< 0.7` → flag for manual review, do not auto-persist

---

## 11. Development Roadmap

### Milestone 0 — Prompt Validation (Start here, 2–3 weeks)
*Goal: Prove the core extraction works on real Romanian documents before writing any infrastructure.*

- [ ] Collect 30–50 sample documents (your own, anonymized)
- [ ] Write classification prompt, test against all document types
- [ ] Write extraction prompt for buletin — benchmark against ground truth
- [ ] Write extraction prompt for poliță RCA — benchmark
- [ ] Write extraction prompt for permis de conducere — benchmark
- [ ] Write extraction prompt for certificat înmatriculare — benchmark
- [ ] Compare Gemini 2.0 Flash vs GPT-4o on accuracy + cost per doc type
- [ ] Document final prompt versions and accuracy scores in `/prompts` folder

**Exit criterion:** >90% field accuracy on all 4 document types. If not reached, iterate prompts before proceeding. Do not start Milestone 1 until this passes.

---

### Milestone 1 — Backend Skeleton (2–3 weeks)
*Goal: Working extraction pipeline end-to-end, no frontend yet.*

- [ ] Set up AWS account, eu-central-1 region, IAM roles
- [ ] Create S3 bucket (private, encrypted, versioning on)
- [ ] Set up Aurora Serverless v2 PostgreSQL, run initial migrations
- [ ] Set up SQS queue + Dead Letter Queue
- [ ] Scaffold Micronaut project (Kotlin, Gradle — 3 Lambda modules: upload, extraction, query)
- [ ] Implement Upload Lambda: receive document → store S3 → enqueue SQS message
- [ ] Implement Extraction Lambda: read SQS → fetch S3 → convert → call LLM → persist JSON to DB
- [ ] Implement basic Query Lambda: JWT auth via Cognito, fetch user documents
- [ ] Set up GitHub Actions: build native → deploy to Lambda
- [ ] Write integration test: upload a real buletin photo, verify fields in DB

**Exit criterion:** Upload a document via curl, see correct extracted fields in the database within 10 seconds.

---

### Milestone 2 — Web Frontend MVP (2–3 weeks)
*Goal: A real usable interface, even if rough.*

- [ ] Scaffold React + Vite project, deploy to S3 + CloudFront
- [ ] Cognito signup / login flow (email + password)
- [ ] Document upload screen (drag & drop or file picker)
- [ ] "Processing..." state with polling or WebSocket notification
- [ ] Document list view (cards showing doc type + key fields)
- [ ] Document detail view (all extracted fields, original image)
- [ ] Manual field correction UI (user can fix a wrong field)
- [ ] Freemium gate: 1 free scan, prompt to upgrade after

**Exit criterion:** 5 non-technical testers can sign up, scan a document, and see their extracted fields without help.

---

### Milestone 3 — Q&A, Expiry Intelligence & Form Auto-Fill (3–4 weeks)
*Goal: The "personal data layer" experience that differentiates from a dumb scanner — plus the first moment where DocuRo actively does something for the user.*

**Q&A & Alerts**
- [ ] Natural language Q&A endpoint (Query Lambda): user asks question → LLM reads their extracted JSON → returns answer
- [ ] Suggested questions per document type ("Ask: what is my CNP?")
- [ ] Expiry detection: scan all user documents for date_expirarii fields
- [ ] Expiry alert emails (SES): 30 days, 7 days before expiry
- [ ] Cross-document insight: "You have a car but no RCA on file"
- [ ] Document health dashboard: missing, expiring, review-needed

**Insurance Form Auto-Fill (Track A)**
- [ ] In-app browser with JavaScript field injection
- [ ] Field mapping registry (JSON config per insurer: eMAG Broker, Groupama, Allianz)
- [ ] "Completează automat" trigger button in in-app browser
- [ ] Map RCA form fields for top 3 Romanian online platforms
- [ ] User flow: select document sources → preview mapped fields → inject

**Government Form Pre-Fill**
- [ ] Life event triggers UI: "Am cumpărat imobil", "Am cumpărat mașină", "Am înregistrat PFA"
- [ ] PDF template integration for ITL-001 (imobil) and ITL-004 (vehicul)
- [ ] Field mapping: DocuRo JSON → PDF form field positions (Apache PDFBox)
- [ ] Pre-filled PDF generation Lambda
- [ ] "Ce acte mai îți trebuie" checklist shown alongside generated PDF
- [ ] Download + print flow in app

**Exit criterion:** User buys a car, triggers "Am cumpărat mașină", gets a pre-filled ITL-004 PDF ready to print. User opens eMAG Broker in the in-app browser and taps "Completează automat" to fill the RCA quote form in under 10 seconds.

---

### Milestone 4 — Payments & Private Beta (1–2 weeks)
*Goal: First revenue.*

- [ ] Stripe integration (or Romanian alternative: Netopia)
- [ ] Subscription management: Personal plan (20 RON/month)
- [ ] Paywall enforcement in Upload Lambda (check subscription status)
- [ ] Invite 20–30 Romanian beta users (friends, colleagues, PFA owners)
- [ ] Collect feedback via a simple Tally form linked from the app
- [ ] Monitor extraction accuracy in production, fix weak prompts

**Exit criterion:** At least 5 paying users. At least one user says "aceasta mi-a salvat timp."

---

### Milestone 5 — Mobile App (4–6 weeks, parallel with user growth)
*Goal: Camera-native experience that feels like the real product.*

- [ ] React Native project (reuses API layer already built)
- [ ] Native camera integration with document edge detection
- [ ] Biometric auth (Face ID / fingerprint) for app unlock
- [ ] Push notifications (extraction complete, expiry alerts)
- [ ] iOS App Store + Google Play submission
- [ ] GDPR-compliant privacy policy + app store review

**Exit criterion:** App approved on both stores. Existing web users can migrate to mobile.

---

### Milestone 6 — B2B Foundation (timing: when you have 500+ users)
*Goal: First insurer pilot conversation backed by real user data. Convert Track A (in-app browser scraping) to Track B (clean partner API).*

- [ ] "Completează cu DocuRo" OAuth-style consent flow (replaces in-app browser injection)
- [ ] Partner API: insurer requests field set → user approves in DocuRo app → data sent pre-validated
- [ ] ConsentShare data model + audit log visible to user
- [ ] Approach eMAG Broker / Groupama with pitch: "reduce form drop-off, get cleaner data"
- [ ] Per-completed-share commercial model with first partner
- [ ] GDPR DPA registration, legal review of data sharing agreements
- [ ] Future hook: ghișeul.ro direct submission when government APIs become available

---

### Key Principle Across All Milestones

**Each milestone must be usable and testable by a real person before starting the next.** Do not build Milestone 2 until Milestone 1's exit criterion passes. This prevents building on top of a broken foundation.

---

## 12. Open Questions & Risks

| Question | Status |
|---|---|
| Which Vision LLM performs best on Romanian legal contracts? | Needs benchmarking on 20–30 real documents |
| What is the minimum viable document set for launch? | Suggested: buletin, permis, RCA, certificat înmatriculare |
| How to handle handwritten notarial annotations? | OCR quality risk — may need human review fallback |
| GDPR DPA registration required? | Yes, if processing special category data at scale — consult a Romanian DPD |
| App store compliance for document scanning apps? | Review Apple/Google policies on identity document handling |
| Romanian insurer API landscape — do any expose APIs today? | Research needed |

---

*Last updated: March 2026. This is a living document — update after each validation milestone.*
