package br.ufla.citogenetica.config;

import br.ufla.citogenetica.usuario.api.PlatformAdminBootstrap;
import br.ufla.citogenetica.usuario.api.error.PlatformAdminBootstrapException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap-admin-command")
@ConditionalOnNotWebApplication
@ConditionalOnProperty(name = "app.bootstrap-admin.cli", havingValue = "true")
public class BootstrapAdminRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final PlatformAdminBootstrap platformAdminBootstrap;
    private final Environment environment;
    private int exitCode;

    public BootstrapAdminRunner(PlatformAdminBootstrap platformAdminBootstrap, Environment environment) {
        this.platformAdminBootstrap = platformAdminBootstrap;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = environment.getProperty("app.bootstrap-admin.email", "");
        try {
            platformAdminBootstrap.promoteFirstAdmin(email);
            LOGGER.info("Primeiro administrador global promovido com sucesso.");
        } catch (PlatformAdminBootstrapException exception) {
            LOGGER.error("Falha no bootstrap do primeiro administrador: {}", exception.getMessage());
            exitCode = 1;
        } catch (RuntimeException exception) {
            LOGGER.error("Falha interna ao executar o bootstrap do primeiro administrador.");
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
