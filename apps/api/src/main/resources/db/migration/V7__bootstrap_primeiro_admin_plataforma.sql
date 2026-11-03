-- Marcador singleton sem FK para preservar a auditoria caso a conta seja removida.
CREATE TABLE bootstrap_admin_plataforma (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    consumido_em TIMESTAMPTZ,
    usuario_id BIGINT,
    CONSTRAINT ck_bootstrap_admin_plataforma_consumo
        CHECK ((consumido_em IS NULL AND usuario_id IS NULL)
            OR (consumido_em IS NOT NULL AND usuario_id IS NOT NULL))
);

INSERT INTO bootstrap_admin_plataforma (id) VALUES (1);
