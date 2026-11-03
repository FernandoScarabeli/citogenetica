-- Filtros de texto da API comparam unaccent(lower(campo)); sem a extensão, a consulta falha.
-- unaccent é extensão confiável (trusted) desde o PostgreSQL 13: o dono do banco pode criá-la.
CREATE EXTENSION IF NOT EXISTS unaccent;
