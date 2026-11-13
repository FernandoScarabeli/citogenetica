-- Marcações desenhadas por cima de uma foto de lâmina (setas e textos), guardadas à parte da imagem original.
-- Coordenadas de 0 a 1, relativas à largura e à altura da foto; o formato é conferido pela API.
ALTER TABLE foto_lamina ADD COLUMN anotacoes JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE foto_lamina ADD CONSTRAINT ck_foto_lamina_anotacoes CHECK (jsonb_typeof(anotacoes) = 'array');
