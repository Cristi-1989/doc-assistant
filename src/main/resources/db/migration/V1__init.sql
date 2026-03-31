CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255)        NOT NULL,  -- BCrypt hash for login verification
    kek_salt      BYTEA               NOT NULL,  -- Argon2id salt for KEK derivation
    encrypted_dek BYTEA               NOT NULL,  -- DEK encrypted with KEK (AES-256-GCM)
    dek_nonce     BYTEA               NOT NULL,  -- nonce used when encrypting the DEK
    created_at    TIMESTAMP           NOT NULL DEFAULT now()
);

CREATE TABLE documents (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL REFERENCES users(id),
    type                  VARCHAR(50),                    -- 'buletin', 'permis_conducere', etc.
    status                VARCHAR(20)  NOT NULL,          -- 'processing', 'ready', 'review_needed', 'failed'
    original_filename     VARCHAR(255),
    storage_key           VARCHAR(500),                   -- MinIO/S3 object key (encrypted file)
    mime_type             VARCHAR(100),                   -- 'image/jpeg', 'application/pdf', etc.
    file_nonce            BYTEA,                          -- AES-GCM nonce used to encrypt the stored file
    extraction_confidence FLOAT,                          -- 0.0 to 1.0
    fields                JSONB,                          -- extracted fields; sensitive values are individually encrypted
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_user_id ON documents (user_id);
CREATE INDEX idx_documents_type    ON documents (type);
