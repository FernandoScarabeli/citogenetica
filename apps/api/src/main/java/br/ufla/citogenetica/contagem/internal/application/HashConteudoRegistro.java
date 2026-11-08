package br.ufla.citogenetica.contagem.internal.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import br.ufla.citogenetica.contagem.internal.domain.StatusRegistro;

/**
 * Identidade do pedido de criação de um registro (ADR 0003): SHA-256, em hexadecimal minúsculo, de uma
 * representação canônica de lâmina, fase, situação, tipos (ordenados como texto), quantidade, hora e fuso do
 * aparelho. É gravado na criação e nunca muda, para que a repetição do mesmo pedido continue reconhecida
 * mesmo depois de o registro ser editado ou parcialmente desfeito.
 */
final class HashConteudoRegistro {

    private static final String VERSAO = "registro-contagem/v1";

    private HashConteudoRegistro() {
    }

    /** {@code tipoIds} deve vir ordenado pelo texto canônico. */
    static String calcular(UUID laminaId, UUID faseId, StatusRegistro status, List<UUID> tipoIds, int quantidade,
                           Instant horaAparelho, String fusoAparelho) {
        String canonico = String.join("\n",
                VERSAO,
                laminaId.toString(),
                faseId.toString(),
                status.name(),
                tipoIds.stream().map(UUID::toString).collect(Collectors.joining(",")),
                Integer.toString(quantidade),
                horaAparelho == null ? "" : horaAparelho.toString(),
                fusoAparelho == null ? "" : fusoAparelho);
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }
}
