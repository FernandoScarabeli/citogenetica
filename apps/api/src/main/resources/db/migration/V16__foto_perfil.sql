-- Foto de perfil guardada no armazenamento de arquivos (RustFS), uma por conta.
-- Antes ela ficava só no navegador, numa chave única, e aparecia para qualquer conta que entrasse depois no mesmo navegador.
ALTER TABLE usuario ADD COLUMN foto_chave VARCHAR(80);

ALTER TABLE usuario ADD CONSTRAINT ck_usuario_foto_chave
    CHECK (foto_chave ~ '^perfis/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$');
