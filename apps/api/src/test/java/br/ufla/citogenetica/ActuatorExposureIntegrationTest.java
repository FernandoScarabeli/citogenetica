package br.ufla.citogenetica;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Configuração base (fora do perfil dev): health público só com o status, nenhum outro
 * endpoint do actuator exposto e SQL fora do log.
 */
@AutoConfigureMockMvc
class ActuatorExposureIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void healthPublicoNaoDeveExporDetalhesDaInfraestrutura() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void somenteOHealthDeveEstarExpostoNoActuator() {
        String expostos = environment.getProperty("management.endpoints.web.exposure.include", "");
        assertFalse(expostos.contains("info"), "Somente health deve ser exposto; exposto: " + expostos);
        assertFalse(expostos.contains("*"), "Somente health deve ser exposto; exposto: " + expostos);
    }

    @Test
    void sqlNaoDeveIrParaOLogForaDoPerfilDev() {
        assertFalse(environment.getProperty("spring.jpa.show-sql", Boolean.class, false));
        assertFalse(environment.getProperty("spring.jpa.properties.hibernate.format_sql", Boolean.class, false));
    }
}
