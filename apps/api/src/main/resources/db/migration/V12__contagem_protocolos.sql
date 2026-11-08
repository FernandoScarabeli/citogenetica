-- Módulo contagem, parte 1: protocolos versionados, fases e tipos de anormalidade.
-- Tipos fixos pertencem à versão do protocolo e são imutáveis depois de publicada.
-- Tipos personalizados pertencem a um usuário (e, opcionalmente, a um laboratório).

CREATE TABLE protocolo (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    codigo VARCHAR(40) NOT NULL,
    nome VARCHAR(120) NOT NULL,
    CONSTRAINT uq_protocolo_codigo UNIQUE (codigo),
    CONSTRAINT ck_protocolo_codigo CHECK (codigo ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT ck_protocolo_nome CHECK (nome = BTRIM(nome) AND CHAR_LENGTH(nome) BETWEEN 1 AND 120)
);

CREATE TABLE protocolo_versao (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    protocolo_id UUID NOT NULL,
    numero INTEGER NOT NULL,
    publicada_em TIMESTAMPTZ,
    CONSTRAINT ck_protocolo_versao_numero CHECK (numero >= 1),
    CONSTRAINT uq_protocolo_versao_numero UNIQUE (protocolo_id, numero),
    CONSTRAINT fk_protocolo_versao_protocolo
        FOREIGN KEY (protocolo_id) REFERENCES protocolo(id) ON DELETE RESTRICT
);

CREATE TABLE protocolo_fase (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    versao_id UUID NOT NULL,
    nome VARCHAR(120) NOT NULL,
    ordem INTEGER NOT NULL,
    conta_como_divisao BOOLEAN NOT NULL,
    CONSTRAINT ck_protocolo_fase_nome CHECK (nome = BTRIM(nome) AND CHAR_LENGTH(nome) BETWEEN 1 AND 120),
    CONSTRAINT ck_protocolo_fase_ordem CHECK (ordem >= 1),
    CONSTRAINT uq_protocolo_fase_nome UNIQUE (versao_id, nome),
    CONSTRAINT uq_protocolo_fase_ordem UNIQUE (versao_id, ordem),
    CONSTRAINT fk_protocolo_fase_versao
        FOREIGN KEY (versao_id) REFERENCES protocolo_versao(id) ON DELETE RESTRICT
);

-- Uma única tabela para tipos fixos e personalizados: registros de contagem apontarão para ela por uma FK só.
-- usuario_id e laboratorio_id nulos = tipo fixo da versão. laboratorio_id nulo em tipo personalizado = lâmina avulsa.
CREATE TABLE tipo_anormalidade (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    last_modified_at TIMESTAMPTZ NOT NULL,
    fase_id UUID NOT NULL,
    usuario_id BIGINT,
    laboratorio_id UUID,
    nome VARCHAR(120) NOT NULL,
    nome_normalizado VARCHAR(120) NOT NULL,
    ordem INTEGER,
    CONSTRAINT ck_tipo_anormalidade_nome CHECK (nome = BTRIM(nome) AND CHAR_LENGTH(nome) BETWEEN 1 AND 120),
    CONSTRAINT ck_tipo_anormalidade_nome_normalizado CHECK (CHAR_LENGTH(nome_normalizado) >= 1),
    CONSTRAINT ck_tipo_anormalidade_ordem CHECK (ordem IS NULL OR ordem >= 1),
    CONSTRAINT ck_tipo_anormalidade_escopo CHECK (
        (usuario_id IS NULL AND laboratorio_id IS NULL AND ordem IS NOT NULL)
        OR (usuario_id IS NOT NULL AND ordem IS NULL)
    ),
    CONSTRAINT fk_tipo_anormalidade_fase
        FOREIGN KEY (fase_id) REFERENCES protocolo_fase(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tipo_anormalidade_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    CONSTRAINT fk_tipo_anormalidade_laboratorio
        FOREIGN KEY (laboratorio_id) REFERENCES laboratorio(id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_tipo_anormalidade_fixo_nome
    ON tipo_anormalidade (fase_id, nome_normalizado)
    WHERE usuario_id IS NULL;
CREATE UNIQUE INDEX uq_tipo_anormalidade_fixo_ordem
    ON tipo_anormalidade (fase_id, ordem)
    WHERE usuario_id IS NULL;
-- NULLS NOT DISTINCT trata todas as lâminas avulsas (laboratorio_id nulo) do usuário como um só escopo.
CREATE UNIQUE INDEX uq_tipo_anormalidade_personalizado_nome
    ON tipo_anormalidade (usuario_id, laboratorio_id, fase_id, nome_normalizado) NULLS NOT DISTINCT
    WHERE usuario_id IS NOT NULL;
CREATE INDEX idx_tipo_anormalidade_laboratorio
    ON tipo_anormalidade (laboratorio_id)
    WHERE laboratorio_id IS NOT NULL;

-- Imutabilidade: versão publicada, suas fases e seus tipos fixos nunca mudam.
-- A consulta trava a linha da versão (FOR SHARE) para serializar com a publicação concorrente.
CREATE FUNCTION protocolo_versao_esta_publicada(p_versao_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
AS $$
DECLARE
    v_publicada_em TIMESTAMPTZ;
BEGIN
    SELECT publicada_em INTO v_publicada_em
      FROM protocolo_versao
     WHERE id = p_versao_id
     FOR SHARE;
    RETURN v_publicada_em IS NOT NULL;
END;
$$;

CREATE FUNCTION proteger_protocolo_versao_publicada()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- A transição rascunho -> publicada é permitida uma única vez.
    IF OLD.publicada_em IS NOT NULL THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'ck_protocolo_versao_imutavel',
            MESSAGE = 'Versão de protocolo publicada não pode ser alterada nem excluída.';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_protocolo_versao_imutavel
    BEFORE UPDATE OR DELETE ON protocolo_versao
    FOR EACH ROW EXECUTE FUNCTION proteger_protocolo_versao_publicada();

CREATE FUNCTION proteger_protocolo_fase_publicada()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (TG_OP <> 'INSERT' AND protocolo_versao_esta_publicada(OLD.versao_id))
       OR (TG_OP <> 'DELETE' AND protocolo_versao_esta_publicada(NEW.versao_id)) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            CONSTRAINT = 'ck_protocolo_fase_imutavel',
            MESSAGE = 'Fases de uma versão de protocolo publicada não podem ser alteradas.';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_protocolo_fase_imutavel
    BEFORE INSERT OR UPDATE OR DELETE ON protocolo_fase
    FOR EACH ROW EXECUTE FUNCTION proteger_protocolo_fase_publicada();

-- Só tipos fixos são protegidos: tipos personalizados continuam podendo ser criados em fases publicadas.
CREATE FUNCTION proteger_tipo_anormalidade_fixo_publicado()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_versao_id UUID;
BEGIN
    IF TG_OP <> 'INSERT' AND OLD.usuario_id IS NULL THEN
        SELECT versao_id INTO v_versao_id FROM protocolo_fase WHERE id = OLD.fase_id;
        IF protocolo_versao_esta_publicada(v_versao_id) THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                CONSTRAINT = 'ck_tipo_anormalidade_fixo_imutavel',
                MESSAGE = 'Tipos fixos de uma versão de protocolo publicada não podem ser alterados.';
        END IF;
    END IF;
    IF TG_OP <> 'DELETE' AND NEW.usuario_id IS NULL THEN
        SELECT versao_id INTO v_versao_id FROM protocolo_fase WHERE id = NEW.fase_id;
        IF protocolo_versao_esta_publicada(v_versao_id) THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                CONSTRAINT = 'ck_tipo_anormalidade_fixo_imutavel',
                MESSAGE = 'Tipos fixos de uma versão de protocolo publicada não podem ser alterados.';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_tipo_anormalidade_fixo_imutavel
    BEFORE INSERT OR UPDATE OR DELETE ON tipo_anormalidade
    FOR EACH ROW EXECUTE FUNCTION proteger_tipo_anormalidade_fixo_publicado();

-- Seed: protocolos atuais da tela de contagem (apps/web/src/domain.ts), com grafia e ordem exatas.
-- A versão 1 entra como rascunho, é populada e só então publicada (única transição permitida).
INSERT INTO protocolo (id, version, created_at, last_modified_at, codigo, nome)
VALUES (gen_random_uuid(), 0, NOW(), NOW(), 'meiose', 'Meiose'),
       (gen_random_uuid(), 0, NOW(), NOW(), 'mitose', 'Mitose');

INSERT INTO protocolo_versao (id, version, created_at, last_modified_at, protocolo_id, numero, publicada_em)
SELECT gen_random_uuid(), 0, NOW(), NOW(), id, 1, NULL
  FROM protocolo;

INSERT INTO protocolo_fase (id, version, created_at, last_modified_at, versao_id, nome, ordem, conta_como_divisao)
SELECT gen_random_uuid(), 0, NOW(), NOW(), v.id, f.nome, f.ordem, f.nome <> 'Interfase'
  FROM (VALUES
        ('meiose', 1, 'Leptóteno/Zigóteno'),
        ('meiose', 2, 'Paquíteno'),
        ('meiose', 3, 'Diplóteno/Diacinese'),
        ('meiose', 4, 'Metáfase I'),
        ('meiose', 5, 'Anáfase I'),
        ('meiose', 6, 'Telófase I'),
        ('meiose', 7, 'Prófase II'),
        ('meiose', 8, 'Metáfase II'),
        ('meiose', 9, 'Anáfase II'),
        ('meiose', 10, 'Telófase II'),
        ('meiose', 11, 'Tétrade'),
        ('mitose', 1, 'Interfase'),
        ('mitose', 2, 'Prófase'),
        ('mitose', 3, 'Metáfase'),
        ('mitose', 4, 'Anáfase'),
        ('mitose', 5, 'Telófase')
       ) AS f(codigo, ordem, nome)
  JOIN protocolo p ON p.codigo = f.codigo
  JOIN protocolo_versao v ON v.protocolo_id = p.id AND v.numero = 1;

-- nome_normalizado: sem acentos e em minúsculas, igual à normalização do servidor (NomeTipoAnormalidade).
-- unaccent antes de lower evita depender do locale do banco para letras acentuadas.
INSERT INTO tipo_anormalidade (id, version, created_at, last_modified_at, fase_id, usuario_id, laboratorio_id,
                               nome, nome_normalizado, ordem)
SELECT gen_random_uuid(), 0, NOW(), NOW(), fase.id, NULL, NULL,
       t.nome, LOWER(UNACCENT(t.nome)), t.ordem
  FROM (VALUES
        ('meiose', 'Diplóteno/Diacinese', 1, 'Univalente'),
        ('meiose', 'Diplóteno/Diacinese', 2, 'Trivalente'),
        ('meiose', 'Diplóteno/Diacinese', 3, 'Tetravalente'),
        ('meiose', 'Metáfase I', 1, 'Cromossomos não orientados'),
        ('meiose', 'Anáfase I', 1, 'Cromossomos retardatários'),
        ('meiose', 'Anáfase I', 2, 'Segregação de cromátides'),
        ('meiose', 'Telófase I', 1, 'Micronúcleo'),
        ('meiose', 'Telófase I', 2, 'Fuso tripolar'),
        ('meiose', 'Prófase II', 1, 'Micronúcleo'),
        ('meiose', 'Prófase II', 2, 'Núcleos assincrônicos'),
        ('meiose', 'Metáfase II', 1, 'Cromossomos não orientados'),
        ('meiose', 'Metáfase II', 2, 'Assincronia'),
        ('meiose', 'Anáfase II', 1, 'Cromossomos retardatários'),
        ('meiose', 'Anáfase II', 2, 'Assincronia'),
        ('meiose', 'Telófase II', 1, 'Micronúcleo'),
        ('meiose', 'Telófase II', 2, 'Ponte'),
        ('meiose', 'Tétrade', 1, 'Micronúcleo'),
        ('meiose', 'Tétrade', 2, 'Tríade'),
        ('meiose', 'Tétrade', 3, 'Díade'),
        ('mitose', 'Interfase', 1, 'Núcleos apoptóticos (condensados)'),
        ('mitose', 'Prófase', 1, 'Stickiness'),
        ('mitose', 'Prófase', 2, 'Cromossomos ou fragmentos desprendidos'),
        ('mitose', 'Prófase', 3, 'Núcleo com brotações'),
        ('mitose', 'Prófase', 4, 'Micronúcleo'),
        ('mitose', 'Metáfase', 1, 'Cromossomos não orientados'),
        ('mitose', 'Metáfase', 2, 'Metáfase colchicínica'),
        ('mitose', 'Metáfase', 3, 'Stickiness'),
        ('mitose', 'Anáfase', 1, 'Cromossomos atrasados'),
        ('mitose', 'Anáfase', 2, 'Fuso anormal'),
        ('mitose', 'Anáfase', 3, 'Stickiness'),
        ('mitose', 'Anáfase', 4, 'Ponte'),
        ('mitose', 'Anáfase', 5, 'Ponte com fragmento'),
        ('mitose', 'Telófase', 1, 'Fuso anormal'),
        ('mitose', 'Telófase', 2, 'Ponte'),
        ('mitose', 'Telófase', 3, 'Ponte com fragmento'),
        ('mitose', 'Telófase', 4, 'Micronúcleo: 1'),
        ('mitose', 'Telófase', 5, 'Micronúcleo: 2'),
        ('mitose', 'Telófase', 6, 'Micronúcleo: 3'),
        ('mitose', 'Telófase', 7, 'Micronúcleo: mais de 3')
       ) AS t(codigo, fase_nome, ordem, nome)
  JOIN protocolo p ON p.codigo = t.codigo
  JOIN protocolo_versao v ON v.protocolo_id = p.id AND v.numero = 1
  JOIN protocolo_fase fase ON fase.versao_id = v.id AND fase.nome = t.fase_nome;

UPDATE protocolo_versao SET publicada_em = NOW() WHERE numero = 1 AND publicada_em IS NULL;
