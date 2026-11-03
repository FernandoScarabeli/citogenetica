package br.ufla.citogenetica.configuration.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api.security.trusted-proxy")
public record TrustedProxyProperties(String internalProxies) {
    public TrustedProxyProperties {
        internalProxies = internalProxies == null ? "" : internalProxies.trim();
    }
}
