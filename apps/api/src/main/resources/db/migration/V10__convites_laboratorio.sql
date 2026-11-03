CREATE TABLE convite_laboratorio (
    id UUID PRIMARY KEY,
    laboratorio_id UUID NOT NULL,
    convidado_por_usuario_id BIGINT NOT NULL,
    email_destino VARCHAR(254) NOT NULL,
    token_hash BYTEA,
    estado VARCHAR(20) NOT NULL,
    conceder_admin BOOLEAN NOT NULL DEFAULT FALSE,
    conceder_orientador BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ NOT NULL,
    respondido_em TIMESTAMPTZ,
    respondido_por_usuario_id BIGINT,
    revogado_em TIMESTAMPTZ,
    revogado_por_usuario_id BIGINT,
    participacao_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_convite_laboratorio_email_normalizado
        CHECK (email_destino = LOWER(BTRIM(email_destino)) AND CHAR_LENGTH(email_destino) BETWEEN 3 AND 254),
    CONSTRAINT ck_convite_laboratorio_estado
        CHECK (estado IN ('PENDENTE', 'ACEITO', 'RECUSADO', 'REVOGADO', 'EXPIRADO')),
    CONSTRAINT ck_convite_laboratorio_hash
        CHECK (token_hash IS NULL OR OCTET_LENGTH(token_hash) = 32),
    CONSTRAINT ck_convite_laboratorio_transicao CHECK (
        (estado = 'PENDENTE' AND token_hash IS NOT NULL
            AND respondido_em IS NULL AND respondido_por_usuario_id IS NULL
            AND revogado_em IS NULL AND revogado_por_usuario_id IS NULL AND participacao_id IS NULL)
        OR (estado = 'ACEITO' AND token_hash IS NULL
            AND respondido_em IS NOT NULL AND respondido_por_usuario_id IS NOT NULL
            AND revogado_em IS NULL AND revogado_por_usuario_id IS NULL AND participacao_id IS NOT NULL)
        OR (estado = 'RECUSADO' AND token_hash IS NULL
            AND respondido_em IS NOT NULL AND respondido_por_usuario_id IS NOT NULL
            AND revogado_em IS NULL AND revogado_por_usuario_id IS NULL AND participacao_id IS NULL)
        OR (estado = 'REVOGADO' AND token_hash IS NULL
            AND respondido_em IS NULL AND respondido_por_usuario_id IS NULL
            AND revogado_em IS NOT NULL AND revogado_por_usuario_id IS NOT NULL AND participacao_id IS NULL)
        OR (estado = 'EXPIRADO' AND token_hash IS NULL
            AND respondido_em IS NULL AND respondido_por_usuario_id IS NULL
            AND revogado_em IS NULL AND revogado_por_usuario_id IS NULL AND participacao_id IS NULL)
    ),
    CONSTRAINT fk_convite_laboratorio_laboratorio
        FOREIGN KEY (laboratorio_id) REFERENCES laboratorio(id) ON DELETE RESTRICT,
    CONSTRAINT fk_convite_laboratorio_convidado_por
        FOREIGN KEY (convidado_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_convite_laboratorio_respondido_por
        FOREIGN KEY (respondido_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_convite_laboratorio_revogado_por
        FOREIGN KEY (revogado_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_convite_laboratorio_participacao
        FOREIGN KEY (participacao_id) REFERENCES participacao(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_convite_laboratorio_token_hash
    ON convite_laboratorio (token_hash)
    WHERE token_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_convite_laboratorio_pendente_email
    ON convite_laboratorio (laboratorio_id, email_destino)
    WHERE estado = 'PENDENTE';
CREATE INDEX idx_convite_laboratorio_expiracao
    ON convite_laboratorio (expires_at, id)
    WHERE estado = 'PENDENTE';
CREATE INDEX idx_convite_laboratorio_lab_estado
    ON convite_laboratorio (laboratorio_id, estado, created_at DESC);

CREATE TABLE convite_laboratorio_rate_limit (
    operacao VARCHAR(20) NOT NULL,
    tipo_chave VARCHAR(30) NOT NULL,
    chave_hash BYTEA NOT NULL,
    janela_inicio TIMESTAMPTZ NOT NULL,
    tentativas INTEGER NOT NULL,
    CONSTRAINT pk_convite_laboratorio_rate_limit
        PRIMARY KEY (operacao, tipo_chave, chave_hash, janela_inicio),
    CONSTRAINT ck_convite_laboratorio_rate_limit_hash CHECK (OCTET_LENGTH(chave_hash) = 32),
    CONSTRAINT ck_convite_laboratorio_rate_limit_tentativas CHECK (tentativas > 0)
);

CREATE INDEX idx_convite_laboratorio_rate_limit_janela
    ON convite_laboratorio_rate_limit (janela_inicio);
