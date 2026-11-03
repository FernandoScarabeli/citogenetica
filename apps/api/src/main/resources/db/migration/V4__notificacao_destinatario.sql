-- Cada notificação passa a ter um destinatário obrigatório.
-- As notificações existentes foram criadas só em desenvolvimento e não têm dono conhecido;
-- atribuí-las a alguém vazaria conteúdo, então elas são descartadas.
DELETE FROM notificacao;

ALTER TABLE notificacao
    ADD COLUMN usuario_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_notificacao_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE;

-- A entidade já trata "lida" como obrigatória; NULL escaparia dos filtros de não lidas.
ALTER TABLE notificacao
    ALTER COLUMN lida SET NOT NULL;

-- Toda consulta filtra por destinatário (e quase sempre por lida), ordenando pela data.
DROP INDEX idx_notificacao_lida;
DROP INDEX idx_notificacao_created_date;
CREATE INDEX idx_notificacao_usuario_lida_created_date
    ON notificacao (usuario_id, lida, created_date DESC);
