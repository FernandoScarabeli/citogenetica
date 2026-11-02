-- V1__create_base_schema.sql
-- Schema inicial do sistema citogenetica

-- Tabela de roles
CREATE TABLE role (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMP,
    nome VARCHAR(255) NOT NULL UNIQUE
);

-- Tabela de usuários
CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMP,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    senha VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    token_recuperacao_senha VARCHAR(10),
    data_expiracao_codigo TIMESTAMP,

    CONSTRAINT fk_usuario_role FOREIGN KEY (role_id) REFERENCES role(id)
);

CREATE INDEX idx_usuario_role_id ON usuario(role_id);

-- Tabela de notificações
CREATE TABLE notificacao (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified_date TIMESTAMP,
    tipo VARCHAR(50) NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    mensagem TEXT,
    dados JSONB,
    lida BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_notificacao_lida ON notificacao(lida);
CREATE INDEX idx_notificacao_created_date ON notificacao(created_date DESC);

-- Tabela do exemplo CRUD (para referência)
CREATE TABLE exemplo (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_date TIMESTAMP NOT NULL DEFAULT NOW(),
    last_modified_date TIMESTAMP,
    enum_type VARCHAR(255) NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    valor NUMERIC(10,2),
    data_exemplo DATE NOT NULL,
    ativo BOOLEAN NOT NULL
);

-- Inserção de roles padrão
INSERT INTO role (nome) VALUES ('ADMIN') ON CONFLICT (nome) DO NOTHING;
INSERT INTO role (nome) VALUES ('USER') ON CONFLICT (nome) DO NOTHING;

