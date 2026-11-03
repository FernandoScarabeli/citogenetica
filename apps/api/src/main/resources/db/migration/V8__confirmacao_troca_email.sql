CREATE TABLE desafio_troca_email (
    id UUID PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE,
    email_novo VARCHAR(255) NOT NULL,
    token_hash BYTEA NOT NULL UNIQUE CHECK (OCTET_LENGTH(token_hash) = 32),
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    invalidado_em TIMESTAMPTZ,
    consumido_em TIMESTAMPTZ,
    CONSTRAINT ck_desafio_troca_email_normalizado
        CHECK (email_novo = LOWER(BTRIM(email_novo))),
    CONSTRAINT ck_desafio_troca_email_expiracao CHECK (expira_em > criado_em),
    CONSTRAINT ck_desafio_troca_email_estado
        CHECK (invalidado_em IS NULL OR consumido_em IS NULL),
    CONSTRAINT fk_desafio_troca_email_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);

CREATE INDEX idx_desafio_troca_email_expira_em
    ON desafio_troca_email (expira_em);
