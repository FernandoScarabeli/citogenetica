package br.ufla.citogenetica.usuario.internal.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RateLimitCleanupService {

    private final RateLimitService rateLimitService;

    public RateLimitCleanupService(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Scheduled(fixedDelayString = "${api.security.rate-limit.cleanup-interval-ms}")
    public void limparRegistrosVencidos() {
        rateLimitService.removeExpiredWindows();
    }
}
