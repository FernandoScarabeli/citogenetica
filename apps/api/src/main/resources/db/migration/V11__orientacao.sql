CREATE TABLE vinculo_orientacao (
    id UUID PRIMARY KEY,
    laboratorio_id UUID NOT NULL,
    orientador_participacao_id UUID NOT NULL,
    pesquisador_participacao_id UUID NOT NULL,
    orientador_usuario_id BIGINT NOT NULL,
    pesquisador_usuario_id BIGINT NOT NULL,
    criado_por_usuario_id BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    encerrado_em TIMESTAMPTZ,
    encerrado_por_usuario_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_vinculo_orientacao_estado CHECK (estado IN ('ATIVO', 'ENCERRADO')),
    CONSTRAINT ck_vinculo_orientacao_sem_auto_orientacao CHECK (
        orientador_usuario_id <> pesquisador_usuario_id
        AND orientador_participacao_id <> pesquisador_participacao_id
    ),
    CONSTRAINT ck_vinculo_orientacao_transicao CHECK (
        (estado = 'ATIVO' AND encerrado_em IS NULL AND encerrado_por_usuario_id IS NULL)
        OR (estado = 'ENCERRADO' AND encerrado_em IS NOT NULL AND encerrado_por_usuario_id IS NOT NULL)
    ),
    CONSTRAINT fk_vinculo_orientacao_laboratorio
        FOREIGN KEY (laboratorio_id) REFERENCES laboratorio(id) ON DELETE RESTRICT,
    CONSTRAINT fk_vinculo_orientacao_orientador_participacao
        FOREIGN KEY (orientador_participacao_id) REFERENCES participacao(id) ON DELETE RESTRICT,
    CONSTRAINT fk_vinculo_orientacao_pesquisador_participacao
        FOREIGN KEY (pesquisador_participacao_id) REFERENCES participacao(id) ON DELETE RESTRICT,
    CONSTRAINT fk_vinculo_orientacao_orientador_usuario
        FOREIGN KEY (orientador_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_vinculo_orientacao_pesquisador_usuario
        FOREIGN KEY (pesquisador_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_vinculo_orientacao_criado_por
        FOREIGN KEY (criado_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_vinculo_orientacao_encerrado_por
        FOREIGN KEY (encerrado_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_vinculo_orientacao_ativo_par
    ON vinculo_orientacao (laboratorio_id, orientador_usuario_id, pesquisador_usuario_id)
    WHERE estado = 'ATIVO';
CREATE INDEX idx_vinculo_orientacao_orientador_ativo
    ON vinculo_orientacao (laboratorio_id, orientador_participacao_id, created_at DESC)
    WHERE estado = 'ATIVO';
CREATE INDEX idx_vinculo_orientacao_pesquisador_ativo
    ON vinculo_orientacao (laboratorio_id, pesquisador_participacao_id, created_at DESC)
    WHERE estado = 'ATIVO';

CREATE TABLE convite_orientacao (
    id UUID PRIMARY KEY,
    laboratorio_id UUID NOT NULL,
    orientador_participacao_id UUID NOT NULL,
    orientador_usuario_id BIGINT NOT NULL,
    email_destino VARCHAR(254) NOT NULL,
    token_hash BYTEA,
    estado VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    respondido_em TIMESTAMPTZ,
    respondido_por_usuario_id BIGINT,
    revogado_em TIMESTAMPTZ,
    revogado_por_usuario_id BIGINT,
    vinculo_id UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_convite_orientacao_email_normalizado CHECK (
        email_destino = LOWER(BTRIM(email_destino)) AND CHAR_LENGTH(email_destino) BETWEEN 3 AND 254
    ),
    CONSTRAINT ck_convite_orientacao_estado
        CHECK (estado IN ('PENDENTE', 'ACEITO', 'RECUSADO', 'REVOGADO', 'EXPIRADO')),
    CONSTRAINT ck_convite_orientacao_hash CHECK (token_hash IS NULL OR OCTET_LENGTH(token_hash) = 32),
    CONSTRAINT ck_convite_orientacao_transicao CHECK (
        (estado = 'PENDENTE' AND token_hash IS NOT NULL
            AND respondido_em IS NULL AND respondido_por_usuario_id IS NULL
            AND revogado_em IS NULL AND revogado_por_usuario_id IS NULL AND vinculo_id IS NULL)
        OR (estado = 'ACEITO' AND token_hash IS NULL
            AND respondido_em IS NOT NULL AND respondido_por_usuario_id IS NOT NULL
            AND revogado_em IS NULL AND revogado_por_usuario_id IS NULL AND vinculo_id IS NOT NULL)
        OR (estado = 'RECUSADO' AND token_hash IS NULL
            AND respondido_em IS NOT NULL AND respondido_por_usuario_id IS NOT NULL
            AND revogado_em IS NULL AND revogado_por_usuario_id IS NULL AND vinculo_id IS NULL)
        OR (estado = 'REVOGADO' AND token_hash IS NULL
            AND respondido_em IS NULL AND respondido_por_usuario_id IS NULL
            AND revogado_em IS NOT NULL AND revogado_por_usuario_id IS NOT NULL AND vinculo_id IS NULL)
        OR (estado = 'EXPIRADO' AND token_hash IS NULL
            AND respondido_em IS NULL AND respondido_por_usuario_id IS NULL
            AND revogado_em IS NULL AND revogado_por_usuario_id IS NULL AND vinculo_id IS NULL)
    ),
    CONSTRAINT fk_convite_orientacao_laboratorio
        FOREIGN KEY (laboratorio_id) REFERENCES laboratorio(id) ON DELETE RESTRICT,
    CONSTRAINT fk_convite_orientacao_orientador_participacao
        FOREIGN KEY (orientador_participacao_id) REFERENCES participacao(id) ON DELETE RESTRICT,
    CONSTRAINT fk_convite_orientacao_orientador_usuario
        FOREIGN KEY (orientador_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_convite_orientacao_respondido_por
        FOREIGN KEY (respondido_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_convite_orientacao_revogado_por
        FOREIGN KEY (revogado_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_convite_orientacao_vinculo
        FOREIGN KEY (vinculo_id) REFERENCES vinculo_orientacao(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_convite_orientacao_token_hash
    ON convite_orientacao (token_hash)
    WHERE token_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_convite_orientacao_pendente_email
    ON convite_orientacao (laboratorio_id, orientador_participacao_id, email_destino)
    WHERE estado = 'PENDENTE';
CREATE INDEX idx_convite_orientacao_expiracao
    ON convite_orientacao (expires_at, id)
    WHERE estado = 'PENDENTE';
CREATE INDEX idx_convite_orientacao_lab_estado
    ON convite_orientacao (laboratorio_id, orientador_participacao_id, estado, created_at DESC);
