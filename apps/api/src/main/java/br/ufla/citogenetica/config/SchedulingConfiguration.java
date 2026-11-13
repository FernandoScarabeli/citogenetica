package br.ufla.citogenetica.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("!bootstrap-admin-command")
public class SchedulingConfiguration {
}
