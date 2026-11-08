-- Módulo contagem, parte 2: lâminas.
-- laboratorio_id nulo = lâmina avulsa, visível e editável só pelo dono.
-- A exclusão é lógica (excluida_em). Protocolo, laboratório e dono nunca mudam depois da criação.

CREATE TABLE lamina (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    dono_usuario_id BIGINT NOT NULL,
    laboratorio_id UUID,
    protocolo_versao_id UUID NOT NULL,
    nome VARCHAR(120) NOT NULL,
    especie VARCHAR(120) NOT NULL DEFAULT '',
    tratamento VARCHAR(120) NOT NULL DEFAULT '',
    observacoes VARCHAR(5000) NOT NULL DEFAULT '',
    meta INTEGER,
    excluida_em TIMESTAMPTZ,
    CONSTRAINT ck_lamina_nome CHECK (nome = BTRIM(nome) AND CHAR_LENGTH(nome) BETWEEN 1 AND 120),
    CONSTRAINT ck_lamina_meta CHECK (meta IS NULL OR meta > 0),
    CONSTRAINT fk_lamina_dono
        FOREIGN KEY (dono_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lamina_laboratorio
        FOREIGN KEY (laboratorio_id) REFERENCES laboratorio(id) ON DELETE RESTRICT,
    CONSTRAINT fk_lamina_protocolo_versao
        FOREIGN KEY (protocolo_versao_id) REFERENCES protocolo_versao(id) ON DELETE RESTRICT
);

-- Listagem das avulsas do dono, da mais recente para a mais antiga.
CREATE INDEX idx_lamina_avulsa_dono
    ON lamina (dono_usuario_id, created_at DESC, id)
    WHERE excluida_em IS NULL AND laboratorio_id IS NULL;

-- Listagem do laboratório (filtrada por donos permitidos) e descoberta dos donos com lâmina ativa.
CREATE INDEX idx_lamina_laboratorio_dono
    ON lamina (laboratorio_id, dono_usuario_id, created_at DESC, id)
    WHERE excluida_em IS NULL AND laboratorio_id IS NOT NULL;
