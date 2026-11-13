-- Módulo contagem, parte 4: operações de desfazer idempotentes.
-- Repetir um "desfazer" (falha de rede, fila offline) não pode remover células duas vezes: o id da operação vem do
-- cliente (ADR 0003) e a resposta original fica guardada para ser devolvida igual na repetição, mesmo que os
-- registros afetados tenham mudado depois.

-- Histórico e desfazer percorrem os registros ativos da lâmina do mais recente ao mais antigo.
CREATE INDEX idx_registro_contagem_lamina_recentes
    ON registro_contagem (lamina_id, registrado_em DESC, id DESC)
    WHERE excluido_em IS NULL;

CREATE TABLE operacao_desfazer (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    lamina_id UUID NOT NULL,
    -- Identifica o pedido (lâmina, fase, situação, tipos ordenados e quantidade).
    hash_conteudo CHAR(64) NOT NULL,
    quantidade_desfeita INTEGER NOT NULL,
    CONSTRAINT ck_operacao_desfazer_hash CHECK (hash_conteudo ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_operacao_desfazer_quantidade CHECK (quantidade_desfeita > 0),
    CONSTRAINT fk_operacao_desfazer_lamina
        FOREIGN KEY (lamina_id) REFERENCES lamina(id) ON DELETE RESTRICT
);

CREATE INDEX idx_operacao_desfazer_lamina ON operacao_desfazer (lamina_id);

-- Resposta original: um registro afetado por linha, na ordem em que foi consumido (do mais recente ao mais antigo).
CREATE TABLE operacao_desfazer_registro (
    operacao_id UUID NOT NULL,
    ordem INTEGER NOT NULL,
    registro_id UUID NOT NULL,
    quantidade_restante INTEGER NOT NULL,
    excluido BOOLEAN NOT NULL,
    CONSTRAINT pk_operacao_desfazer_registro PRIMARY KEY (operacao_id, ordem),
    CONSTRAINT ck_operacao_desfazer_registro_ordem CHECK (ordem >= 0),
    CONSTRAINT ck_operacao_desfazer_registro_restante CHECK (quantidade_restante >= 0),
    CONSTRAINT ck_operacao_desfazer_registro_excluido CHECK (NOT excluido OR quantidade_restante = 0),
    CONSTRAINT fk_operacao_desfazer_registro_operacao
        FOREIGN KEY (operacao_id) REFERENCES operacao_desfazer(id) ON DELETE RESTRICT,
    CONSTRAINT fk_operacao_desfazer_registro_registro
        FOREIGN KEY (registro_id) REFERENCES registro_contagem(id) ON DELETE RESTRICT
);

CREATE INDEX idx_operacao_desfazer_registro_registro ON operacao_desfazer_registro (registro_id);
