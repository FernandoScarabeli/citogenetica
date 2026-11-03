package br.ufla.citogenetica.configuration.security;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.catalina.valves.RemoteIpValve;

@Configuration(proxyBeanMethods = false)
public class TrustedProxyConfiguration {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> trustedProxyCustomizer(TrustedProxyProperties properties) {
        return factory -> {
            if (properties.internalProxies().isBlank()) {
                return;
            }

            RemoteIpValve remoteIpValve = new RemoteIpValve();
            remoteIpValve.setInternalProxies(properties.internalProxies());
            remoteIpValve.setRemoteIpHeader("x-forwarded-for");
            remoteIpValve.setProtocolHeader("x-forwarded-proto");
            factory.addEngineValves(remoteIpValve);
        };
    }
}
