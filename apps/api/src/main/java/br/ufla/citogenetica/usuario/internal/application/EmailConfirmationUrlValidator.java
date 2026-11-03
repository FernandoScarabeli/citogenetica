package br.ufla.citogenetica.usuario.internal.application;

import java.net.URI;

final class EmailConfirmationUrlValidator {

    private EmailConfirmationUrlValidator() {
    }

    static boolean isValid(String frontendUrl) {
        try {
            URI uri = URI.create(frontendUrl);
            String host = uri.getHost();
            boolean https = "https".equalsIgnoreCase(uri.getScheme());
            boolean localHttp = "http".equalsIgnoreCase(uri.getScheme()) && isLocalHost(host);
            return (https || localHttp)
                    && host != null
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isLocalHost(String host) {
        return host != null && ("localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "[::1]".equals(host));
    }
}
