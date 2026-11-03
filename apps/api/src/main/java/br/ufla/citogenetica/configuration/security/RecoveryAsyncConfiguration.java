package br.ufla.citogenetica.configuration.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class RecoveryAsyncConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryAsyncConfiguration.class);

    @Bean(name = "recoveryEmailExecutor")
    public ThreadPoolTaskExecutor recoveryEmailExecutor(RecoveryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.emailExecutorCoreSize());
        executor.setMaxPoolSize(properties.emailExecutorMaxSize());
        executor.setQueueCapacity(properties.emailExecutorQueueCapacity());
        executor.setThreadNamePrefix("email-recuperacao-");
        executor.setRejectedExecutionHandler((task, threadPool) ->
                LOGGER.warn("O envio de recuperação foi descartado porque a fila de e-mail está cheia."));
        return executor;
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}
