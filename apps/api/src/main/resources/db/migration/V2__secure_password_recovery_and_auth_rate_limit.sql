DO $$
DECLARE
    collision_groups INTEGER;
BEGIN
    SELECT COUNT(*) INTO collision_groups
    FROM (
        SELECT LOWER(BTRIM(email))
        FROM usuario
        GROUP BY LOWER(BTRIM(email))
        HAVING COUNT(*) > 1
    ) AS emails_colididos;

    IF collision_groups > 0 THEN
        RAISE EXCEPTION 'Migração interrompida: existem % grupos de e-mails duplicados após normalização. Corrija os dados antes de migrar.',
            collision_groups;
    END IF;
END $$;

UPDATE usuario SET email = LOWER(BTRIM(email));

ALTER TABLE usuario DROP CONSTRAINT usuario_email_key;
CREATE UNIQUE INDEX uq_usuario_email_normalizado ON usuario (LOWER(email));

ALTER TABLE usuario
    DROP COLUMN token_recuperacao_senha,
    DROP COLUMN data_expiracao_codigo;

CREATE TABLE desafio_recuperacao_senha (
    id UUID PRIMARY KEY,
    usuario_id BIGINT NOT NULL UNIQUE,
    codigo_hmac BYTEA NOT NULL CHECK (OCTET_LENGTH(codigo_hmac) = 32),
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    invalidado_em TIMESTAMPTZ,
    consumido_em TIMESTAMPTZ,
    CONSTRAINT fk_desafio_recuperacao_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);

CREATE INDEX idx_desafio_recuperacao_expira_em
    ON desafio_recuperacao_senha (expira_em);

CREATE TABLE auth_rate_limit (
    rota VARCHAR(40) NOT NULL,
    tipo_chave VARCHAR(8) NOT NULL,
    chave_hmac BYTEA NOT NULL CHECK (OCTET_LENGTH(chave_hmac) = 32),
    janela_inicio TIMESTAMPTZ NOT NULL,
    tentativas INTEGER NOT NULL CHECK (tentativas > 0),
    CONSTRAINT pk_auth_rate_limit
        PRIMARY KEY (rota, tipo_chave, chave_hmac, janela_inicio)
);

CREATE INDEX idx_auth_rate_limit_janela_inicio
    ON auth_rate_limit (janela_inicio);
