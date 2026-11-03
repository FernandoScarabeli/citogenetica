CREATE TABLE laboratorio (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    nome VARCHAR(120) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    criado_por_usuario_id BIGINT NOT NULL,
    desativado_em TIMESTAMPTZ,
    desativado_por_usuario_id BIGINT,
    CONSTRAINT ck_laboratorio_nome
        CHECK (nome = BTRIM(nome) AND CHAR_LENGTH(nome) BETWEEN 1 AND 120),
    CONSTRAINT ck_laboratorio_estado CHECK (estado IN ('ATIVO', 'DESATIVADO')),
    CONSTRAINT ck_laboratorio_desativacao CHECK (
        (estado = 'ATIVO' AND desativado_em IS NULL AND desativado_por_usuario_id IS NULL)
        OR (estado = 'DESATIVADO' AND desativado_em IS NOT NULL AND desativado_por_usuario_id IS NOT NULL)
    ),
    CONSTRAINT fk_laboratorio_criador
        FOREIGN KEY (criado_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_laboratorio_desativado_por
        FOREIGN KEY (desativado_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
);

CREATE INDEX idx_laboratorio_estado_nome ON laboratorio (estado, nome);

CREATE TABLE participacao (
    id UUID PRIMARY KEY,
    laboratorio_id UUID NOT NULL,
    usuario_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    estado VARCHAR(20) NOT NULL,
    entrou_em TIMESTAMPTZ NOT NULL,
    encerrada_em TIMESTAMPTZ,
    encerrada_por_usuario_id BIGINT,
    papeis_atualizados_em TIMESTAMPTZ,
    CONSTRAINT ck_participacao_estado CHECK (estado IN ('ATIVA', 'SAIU', 'REMOVIDA')),
    CONSTRAINT ck_participacao_encerramento CHECK (
        (estado = 'ATIVA' AND encerrada_em IS NULL AND encerrada_por_usuario_id IS NULL)
        OR (estado IN ('SAIU', 'REMOVIDA')
            AND encerrada_em IS NOT NULL AND encerrada_por_usuario_id IS NOT NULL)
    ),
    CONSTRAINT fk_participacao_laboratorio
        FOREIGN KEY (laboratorio_id) REFERENCES laboratorio(id) ON DELETE RESTRICT,
    CONSTRAINT fk_participacao_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_participacao_encerrada_por
        FOREIGN KEY (encerrada_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_participacao_ativa_usuario_laboratorio
    ON participacao (laboratorio_id, usuario_id)
    WHERE estado = 'ATIVA';
CREATE INDEX idx_participacao_usuario_estado ON participacao (usuario_id, estado);
CREATE INDEX idx_participacao_laboratorio_estado ON participacao (laboratorio_id, estado);

CREATE TABLE participacao_papel (
    id UUID PRIMARY KEY,
    participacao_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    papel VARCHAR(20) NOT NULL,
    concedido_por_usuario_id BIGINT NOT NULL,
    revogado_em TIMESTAMPTZ,
    revogado_por_usuario_id BIGINT,
    CONSTRAINT ck_participacao_papel_valor CHECK (papel IN ('MEMBRO', 'ADMIN', 'ORIENTADOR')),
    CONSTRAINT ck_participacao_papel_revogacao CHECK (
        (revogado_em IS NULL AND revogado_por_usuario_id IS NULL)
        OR (revogado_em IS NOT NULL AND revogado_por_usuario_id IS NOT NULL)
    ),
    CONSTRAINT fk_participacao_papel_participacao
        FOREIGN KEY (participacao_id) REFERENCES participacao(id) ON DELETE RESTRICT,
    CONSTRAINT fk_participacao_papel_concedido_por
        FOREIGN KEY (concedido_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_participacao_papel_revogado_por
        FOREIGN KEY (revogado_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_participacao_papel_ativo
    ON participacao_papel (participacao_id, papel)
    WHERE revogado_em IS NULL;
CREATE INDEX idx_participacao_papel_busca
    ON participacao_papel (papel, participacao_id)
    WHERE revogado_em IS NULL;

CREATE FUNCTION verificar_invariantes_laboratorio(p_laboratorio_id UUID)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_estado VARCHAR(20);
BEGIN
    -- Serializa também alterações SQL diretas; o serviço já obtém este lock antes das linhas filhas.
    SELECT estado INTO v_estado
      FROM laboratorio
     WHERE id = p_laboratorio_id
     FOR UPDATE;
    IF NOT FOUND THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM participacao p
          JOIN participacao_papel pp ON pp.participacao_id = p.id
         WHERE p.laboratorio_id = p_laboratorio_id
           AND p.estado <> 'ATIVA'
           AND pp.revogado_em IS NULL
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'ck_participacao_encerrada_sem_papeis_ativos',
            MESSAGE = 'Participação encerrada não pode manter papéis ativos.';
    END IF;

    IF v_estado <> 'ATIVO' THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM participacao p
         WHERE p.laboratorio_id = p_laboratorio_id
           AND p.estado = 'ATIVA'
           AND NOT EXISTS (
                SELECT 1
                  FROM participacao_papel pp
                 WHERE pp.participacao_id = p.id
                   AND pp.papel = 'MEMBRO'
                   AND pp.revogado_em IS NULL
           )
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'ck_participacao_ativa_membro_base',
            MESSAGE = 'Toda participação ativa deve manter o papel base.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM participacao p
          JOIN participacao_papel pp ON pp.participacao_id = p.id
         WHERE p.laboratorio_id = p_laboratorio_id
           AND p.estado = 'ATIVA'
           AND pp.papel = 'ADMIN'
           AND pp.revogado_em IS NULL
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'ck_laboratorio_admin_ativo',
            MESSAGE = 'Laboratório ativo deve possuir ao menos um administrador.';
    END IF;
END;
$$;

CREATE FUNCTION acionar_verificacao_invariantes_laboratorio()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_laboratorio_id UUID;
    v_laboratorio_id_anterior UUID;
BEGIN
    IF TG_TABLE_NAME = 'laboratorio' THEN
        IF TG_OP <> 'DELETE' THEN
            PERFORM verificar_invariantes_laboratorio(NEW.id);
        END IF;
        IF TG_OP <> 'INSERT' THEN
            PERFORM verificar_invariantes_laboratorio(OLD.id);
        END IF;
    ELSIF TG_TABLE_NAME = 'participacao' THEN
        IF TG_OP <> 'DELETE' THEN
            PERFORM verificar_invariantes_laboratorio(NEW.laboratorio_id);
        END IF;
        IF TG_OP <> 'INSERT' THEN
            PERFORM verificar_invariantes_laboratorio(OLD.laboratorio_id);
        END IF;
    ELSE
        IF TG_OP <> 'DELETE' THEN
            SELECT laboratorio_id INTO v_laboratorio_id
              FROM participacao WHERE id = NEW.participacao_id;
            IF v_laboratorio_id IS NOT NULL THEN
                PERFORM verificar_invariantes_laboratorio(v_laboratorio_id);
            END IF;
        END IF;
        IF TG_OP <> 'INSERT' THEN
            SELECT laboratorio_id INTO v_laboratorio_id_anterior
              FROM participacao WHERE id = OLD.participacao_id;
            IF v_laboratorio_id_anterior IS NOT NULL
               AND v_laboratorio_id_anterior IS DISTINCT FROM v_laboratorio_id THEN
                PERFORM verificar_invariantes_laboratorio(v_laboratorio_id_anterior);
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_laboratorio_invariantes_laboratorio
    AFTER INSERT OR UPDATE OR DELETE ON laboratorio
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION acionar_verificacao_invariantes_laboratorio();

CREATE CONSTRAINT TRIGGER ct_laboratorio_invariantes_participacao
    AFTER INSERT OR UPDATE OR DELETE ON participacao
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION acionar_verificacao_invariantes_laboratorio();

CREATE CONSTRAINT TRIGGER ct_laboratorio_invariantes_papel
    AFTER INSERT OR UPDATE OR DELETE ON participacao_papel
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION acionar_verificacao_invariantes_laboratorio();
