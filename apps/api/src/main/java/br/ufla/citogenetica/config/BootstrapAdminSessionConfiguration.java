package br.ufla.citogenetica.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/** Mantém disponível a revogação de sessões no processo de comando, sem criar servidor HTTP. */
@Configuration
@Profile("bootstrap-admin-command")
@ConditionalOnNotWebApplication
@EnableJdbcHttpSession(cleanupCron = "-")
public class BootstrapAdminSessionConfiguration {
}
