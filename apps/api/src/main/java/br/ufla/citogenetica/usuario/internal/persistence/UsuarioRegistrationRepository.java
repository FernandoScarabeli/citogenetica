package br.ufla.citogenetica.usuario.internal.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UsuarioRegistrationRepository {

    private final JdbcTemplate jdbcTemplate;

    public UsuarioRegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> insertIfEmailIsFree(String name, String email, String encodedPassword, long roleId) {
        return jdbcTemplate.query("""
                        INSERT INTO usuario (nome, email, senha, status, role_id)
                        VALUES (?, ?, ?, 'ATIVO', ?)
                        ON CONFLICT (LOWER(email)) DO NOTHING
                        RETURNING id
                        """,
                (resultSet, rowNumber) -> resultSet.getLong("id"), name, email, encodedPassword, roleId)
                .stream().findFirst();
    }
}
