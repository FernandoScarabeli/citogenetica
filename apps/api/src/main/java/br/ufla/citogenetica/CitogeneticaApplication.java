package br.ufla.citogenetica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;
import java.util.stream.Stream;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
public class CitogeneticaApplication {

    private static final String BOOTSTRAP_COMMAND = "--run-bootstrap-admin";
    private static final String BOOTSTRAP_PROFILE = "bootstrap-admin-command";
    private static final String BOOTSTRAP_MODE_PROPERTY = "--app.bootstrap-admin.cli=true";

    public static void main(String[] args) {
        if (Arrays.stream(args).noneMatch(BOOTSTRAP_COMMAND::equals)) {
            SpringApplication.run(CitogeneticaApplication.class, args);
            return;
        }

        String[] commandArguments = Stream.concat(
                Arrays.stream(args).filter(argument -> !BOOTSTRAP_COMMAND.equals(argument)),
                Stream.of(BOOTSTRAP_MODE_PROPERTY))
                .toArray(String[]::new);

        SpringApplication application = new SpringApplication(CitogeneticaApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles(BOOTSTRAP_PROFILE);

        ConfigurableApplicationContext context = application.run(commandArguments);
        int exitCode = SpringApplication.exit(context);
        System.exit(exitCode);
    }

}
