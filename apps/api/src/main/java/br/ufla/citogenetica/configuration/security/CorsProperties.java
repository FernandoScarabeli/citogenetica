package br.ufla.citogenetica.configuration.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;

@ConfigurationProperties(prefix = "api.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("Configure ao menos uma origem CORS permitida.");
        }
        allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .toList();
        for (String origin : allowedOrigins) {
            URI uri;
            try {
                uri = URI.create(origin);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("A lista de origens CORS contém um valor inválido.");
            }
            if (origin.isBlank() || origin.equals("*") || uri.getHost() == null
                    || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Configure origens CORS explícitas, sem caminho ou curinga.");
            }
        }
    }
}
