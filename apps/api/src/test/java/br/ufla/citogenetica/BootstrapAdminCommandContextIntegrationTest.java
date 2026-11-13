package br.ufla.citogenetica;

import br.ufla.citogenetica.config.BootstrapAdminRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "bootstrap-admin-command"})
class BootstrapAdminCommandContextIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FindByIndexNameSessionRepository<?> sessionRepository;

    @Test
    void contextoSemServidorMantemRevogacaoMasNaoExecutaComandoNemAgendadores() {
        assertTrue(applicationContext.getBeansOfType(ServletWebServerFactory.class).isEmpty());
        assertNotNull(sessionRepository);
        assertTrue(applicationContext.getBeansOfType(BootstrapAdminRunner.class).isEmpty());
        assertFalse(applicationContext.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
    }
}
