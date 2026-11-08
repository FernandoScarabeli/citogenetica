-- Módulo contagem, parte 3: registros de contagem e parâmetro do limite por registro.
-- registrado_em é a hora oficial (do servidor). hora_aparelho e fuso_aparelho são só informativos.
-- hash_conteudo_criacao identifica o pedido de criação original (ADR 0003): a repetição de uma criação continua
-- reconhecida mesmo depois de o registro ser editado ou parcialmente desfeito.

-- Linha única, lida a cada registro. Não é sincronizada entre aparelhos.
CREATE TABLE parametro_contagem (
    id SMALLINT PRIMARY KEY,
    limite_celulas_por_registro INTEGER NOT NULL,
    CONSTRAINT ck_parametro_contagem_linha_unica CHECK (id = 1),
    CONSTRAINT ck_parametro_contagem_limite CHECK (limite_celulas_por_registro > 0)
);

INSERT INTO parametro_contagem (id, limite_celulas_por_registro) VALUES (1, 1000);

CREATE TABLE registro_contagem (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    lamina_id UUID NOT NULL,
    fase_id UUID NOT NULL,
    status VARCHAR(10) NOT NULL,
    -- Sem teto aqui: o limite por registro é configurável em parametro_contagem.
    quantidade INTEGER NOT NULL,
    registrado_em TIMESTAMPTZ NOT NULL,
    hora_aparelho TIMESTAMPTZ,
    fuso_aparelho VARCHAR(64),
    hash_conteudo_criacao CHAR(64) NOT NULL,
    excluido_em TIMESTAMPTZ,
    CONSTRAINT ck_registro_contagem_status CHECK (status IN ('NORMAL', 'ANORMAL')),
    CONSTRAINT ck_registro_contagem_quantidade CHECK (quantidade > 0),
    CONSTRAINT ck_registro_contagem_fuso CHECK (fuso_aparelho IS NULL OR CHAR_LENGTH(fuso_aparelho) >= 1),
    CONSTRAINT ck_registro_contagem_hash CHECK (hash_conteudo_criacao ~ '^[0-9a-f]{64}$'),
    CONSTRAINT fk_registro_contagem_lamina
        FOREIGN KEY (lamina_id) REFERENCES lamina(id) ON DELETE RESTRICT,
    CONSTRAINT fk_registro_contagem_fase
        FOREIGN KEY (fase_id) REFERENCES protocolo_fase(id) ON DELETE RESTRICT
);

-- Resumo por lâmina e fase, só com registros ativos; INCLUDE permite varredura apenas no índice.
CREATE INDEX idx_registro_contagem_lamina_fase
    ON registro_contagem (lamina_id, fase_id) INCLUDE (status, quantidade)
    WHERE excluido_em IS NULL;

-- Um registro pode ter mais de um tipo de anormalidade (só os anormais têm tipos).
CREATE TABLE registro_contagem_tipo (
    registro_id UUID NOT NULL,
    tipo_id UUID NOT NULL,
    CONSTRAINT pk_registro_contagem_tipo PRIMARY KEY (registro_id, tipo_id),
    CONSTRAINT fk_registro_contagem_tipo_registro
        FOREIGN KEY (registro_id) REFERENCES registro_contagem(id) ON DELETE RESTRICT,
    CONSTRAINT fk_registro_contagem_tipo_tipo
        FOREIGN KEY (tipo_id) REFERENCES tipo_anormalidade(id) ON DELETE RESTRICT
);

CREATE INDEX idx_registro_contagem_tipo_tipo ON registro_contagem_tipo (tipo_id);
