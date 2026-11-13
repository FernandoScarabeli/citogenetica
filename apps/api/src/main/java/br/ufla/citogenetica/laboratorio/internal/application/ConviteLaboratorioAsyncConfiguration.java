package br.ufla.citogenetica.laboratorio.internal.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class ConviteLaboratorioAsyncConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConviteLaboratorioAsyncConfiguration.class);

    @Bean(name = "conviteEmailExecutor")
    public ThreadPoolTaskExecutor conviteEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("email-convite-lab-");
        executor.setRejectedExecutionHandler((task, pool) ->
                LOGGER.warn("O envio de convite foi descartado porque a fila de e-mail está cheia."));
        return executor;
    }
}
