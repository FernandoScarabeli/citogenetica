ALTER TABLE usuario
    ADD COLUMN email_confirmado_em TIMESTAMPTZ;

CREATE INDEX idx_usuario_cadastro_pendente
    ON usuario (created_date, id)
    WHERE email_confirmado_em IS NULL;

CREATE TABLE desafio_confirmacao_email (
    id UUID PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE,
    token_hash BYTEA NOT NULL UNIQUE CHECK (OCTET_LENGTH(token_hash) = 32),
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    invalidado_em TIMESTAMPTZ,
    consumido_em TIMESTAMPTZ,
    CONSTRAINT ck_desafio_confirmacao_email_expiracao CHECK (expira_em > criado_em),
    CONSTRAINT ck_desafio_confirmacao_email_estado CHECK (invalidado_em IS NULL OR consumido_em IS NULL),
    CONSTRAINT fk_desafio_confirmacao_email_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);

CREATE INDEX idx_desafio_confirmacao_email_expira_em
    ON desafio_confirmacao_email (expira_em);
