package br.ufla.citogenetica;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class CitogeneticaApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loadsContextAgainstTestcontainersAndAppliesInitialMigration() {
        assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("citogenetica_test");

        assertThat(flyway.info().applied())
                .anySatisfy(migration -> assertThat(migration.getVersion().getVersion()).isEqualTo("1"));
    }

    @Test
    void openApiMantemOsPathsPublicadosAntesDaReorganizacao() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode paths = objectMapper.readTree(json).path("paths");
        Set<String> encontrados = new HashSet<>();
        paths.fieldNames().forEachRemaining(encontrados::add);

        assertThat(encontrados).containsExactlyInAnyOrder(
                "/api/v1/auth/csrf",
                "/api/v1/auth/login",
                "/api/v1/auth/logout",
                "/api/v1/auth/register",
                "/api/v1/auth/confirmar-email",
                "/api/v1/auth/confirmar-troca-email",
                "/api/v1/auth/reenviar-confirmacao",
                "/api/v1/auth/recuperar-senha",
                "/api/v1/auth/redefinir-senha",
                "/api/v1/auth/validar-codigo",
                "/api/events/subscribe",
                "/api/v1/exemplos",
                "/api/v1/exemplos/{id}",
                "/api/v1/laboratorios",
                "/api/v1/laboratorios/{laboratorioId}",
                "/api/v1/laboratorios/{laboratorioId}/participacoes",
                "/api/v1/laboratorios/{laboratorioId}/participacoes/me",
                "/api/v1/laboratorios/{laboratorioId}/participacoes/{participacaoId}",
                "/api/v1/laboratorios/{laboratorioId}/participacoes/{participacaoId}/papeis",
                "/api/v1/admin/laboratorios/{laboratorioId}/desativar",
                "/api/v1/admin/laboratorios/{laboratorioId}/reativar",
                "/api/v1/notificacoes",
                "/api/v1/notificacoes/nao-lidas/count",
                "/api/v1/notificacoes/{id}/lida",
                "/api/v1/notificacoes/marcar-todas-lidas",
                "/api/v1/usuarios",
                "/api/v1/usuarios/{id}",
                "/api/v1/usuarios/me",
                "/api/v1/usuarios/me/email",
                "/api/v1/usuarios/me/senha");
    }
}
