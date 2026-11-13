-- Etapa 6, parte 1: fotos e comentários de uma lâmina.
-- As fotos ficam no armazenamento de arquivos (RustFS); aqui só a chave e os dados para listar.

CREATE TABLE foto_lamina (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    lamina_id UUID NOT NULL,
    chave VARCHAR(80) NOT NULL,
    tamanho_bytes INTEGER NOT NULL,
    legenda VARCHAR(200),
    enviada_por_usuario_id BIGINT NOT NULL,
    CONSTRAINT uq_foto_lamina_chave UNIQUE (chave),
    CONSTRAINT ck_foto_lamina_chave
        CHECK (chave ~ '^laminas/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$'),
    CONSTRAINT ck_foto_lamina_tamanho CHECK (tamanho_bytes > 0),
    CONSTRAINT fk_foto_lamina_lamina FOREIGN KEY (lamina_id) REFERENCES lamina(id) ON DELETE RESTRICT,
    CONSTRAINT fk_foto_lamina_usuario FOREIGN KEY (enviada_por_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
);

CREATE INDEX idx_foto_lamina_lamina ON foto_lamina (lamina_id, created_at);

CREATE TABLE comentario_lamina (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    lamina_id UUID NOT NULL,
    autor_usuario_id BIGINT NOT NULL,
    texto VARCHAR(2000) NOT NULL,
    CONSTRAINT ck_comentario_lamina_texto CHECK (length(btrim(texto)) > 0),
    CONSTRAINT fk_comentario_lamina_lamina FOREIGN KEY (lamina_id) REFERENCES lamina(id) ON DELETE RESTRICT,
    CONSTRAINT fk_comentario_lamina_autor FOREIGN KEY (autor_usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT
);

CREATE INDEX idx_comentario_lamina_lamina ON comentario_lamina (lamina_id, created_at);
